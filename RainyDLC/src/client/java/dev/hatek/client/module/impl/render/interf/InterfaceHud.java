package dev.hatek.client.module.impl.render.interf;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.hatek.client.media.MediaClip;
import dev.hatek.client.media.MediaManager;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.interf.quick.*;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Render2D;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.lwjgl.glfw.GLFW;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Quick HUD port for the repository's Minecraft 26.2 extraction renderer.
 * Original atlas resources and geometry; existing hotbar, media and combat targeting are retained.
 * All bounds, mouse coordinates and persisted positions use raw window pixels.
 */
public final class InterfaceHud {
    private static boolean hooked, screenOpen;
    private static final Map<HudLayout.Element,float[]> BOUNDS=new EnumMap<>(HudLayout.Element.class);
    private static final Map<HudLayout.Element,ListAnimation> LISTS=new EnumMap<>(HudLayout.Element.class);
    private static final Map<String,Long> STAFF_SINCE=new HashMap<>();
    private static Object lastLevel;
    private static long lastFrame;
    private static float delta=1/60f;
    private static int frame;
    private static Hit dragHit;
    private static float grabX,grabY;
    private static HudLayout.Element drawing;
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm");
    private interface Sized { float w(); float h(); }
    private record Row(String key,String left,String right,String icon,Object sprite) {}
    private static final class RowAnimation {
        Row row; float alpha;
        RowAnimation(Row row) { this.row=row; }
    }
    private static final class ListAnimation {
        final Map<String,RowAnimation> rows=new LinkedHashMap<>();
        int seen=-1, dimensions=-1; float w,h,scale;
    }
    private record ListData(float w,float h,String title,String icon,List<RowAnimation> rows) implements Sized {}
    private record PillData(float w,float h,String text,ItemStack item) implements Sized {}
    private record WatermarkData(float w,float h,String[] parts) implements Sized {}
    private record ArmorData(float w,float h,List<ItemStack> items,boolean vertical) implements Sized {}
    private record TargetData(float w,float h,LivingEntity target,String name,String hp,float dist,float health,float absorption) implements Sized {}
    private static LivingEntity healthTarget, lastTarget;
    private static float targetShow;
    private static int targetFrame=-1;
    private static float healthAnimation=1,damageAnimation=1;
    private InterfaceHud() {}
    public static void init() {
        if(hooked) return; hooked=true;
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hatek_client","interface_hud"),(gg,tick)->render(gg));
    }
    public static void setScreenOpen(boolean open) { screenOpen=open; }
    private static boolean enabled() { return Interface.instance()!=null && Interface.instance().isEnabled(); }
    private static boolean editing() { return Minecraft.getInstance().gui.screen() instanceof ChatScreen; }
    private static void render(GuiGraphicsExtractor gg) {
        if(screenOpen) return; renderExplicit(gg);
    }
    public static void renderExplicit(GuiGraphicsExtractor gg) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || !enabled()) { BOUNDS.clear();stopDrag();QuickElementMenu.close();return; }
        renderAll(gg);
    }
    public static Map<HudLayout.Element,float[]> boundsSnapshot() {
        Map<HudLayout.Element,float[]> result=new EnumMap<>(HudLayout.Element.class);
        BOUNDS.forEach((el,b)->result.put(el,b.clone())); return result;
    }
    public static final class Hit {
        public final HudLayout.Element element;
        public final boolean media;
        public final float[] bounds;
        Hit(HudLayout.Element element,boolean media,float[] bounds) { this.element=element;this.media=media;this.bounds=bounds; }
    }
    public static Hit hitTest(double mx,double my) {
        if(!enabled()) return null;
        if(Interface.showMedia2D()) { float[] b=media2DBounds(); if(hit(b,mx,my)) return new Hit(null,true,b); }
        Map<HudLayout.Element,float[]> bounds=computeBounds();
        var elements=new ArrayList<>(bounds.keySet());Collections.reverse(elements);
        for(var el:elements) { var b=bounds.get(el);if(hit(b,mx,my)) return new Hit(el,false,b); }
        return null;
    }
    private static boolean hit(float[] b,double x,double y) { return b!=null && x>=b[0] && x<=b[0]+b[2] && y>=b[1] && y<=b[1]+b[3]; }
    public static Map<HudLayout.Element,float[]> computeBounds() {
        Map<HudLayout.Element,float[]> result=new EnumMap<>(HudLayout.Element.class);
        if(Minecraft.getInstance().player==null || !enabled()) return result;
        for(var el:HudLayout.Element.values()) {
            if(!visible(el)) continue; var e=HudLayout.get(el); Sized d=measure(el,e.scale*Render2D.guiScale());
            if(d==null) continue; float[] p=position(el,e,d.w(),d.h()); result.put(el,new float[]{p[0],p[1],d.w(),d.h()});
        }
        return result;
    }
    private static boolean visible(HudLayout.Element el) {
        return switch(el) {
            case WATERMARK -> Interface.showWatermark();case KEYBINDS -> Interface.showKeybinds();
            case EFFECTS -> Interface.showEffects();case HOTBAR -> Interface.showHotbar();
            case ARMOR -> Interface.showArmor();case TARGET -> Interface.showTarget();
            default -> Interface.showExtra(el);
        };
    }
    public static boolean startDrag(double mx,double my) {
        if(!editing() || QuickElementMenu.open()) return false;
        Hit h=hitTest(mx,my); if(h==null) return false;
        dragHit=h;grabX=(float)mx-h.bounds[0];grabY=(float)my-h.bounds[1];return true;
    }
    public static void stopDrag() { if(dragHit!=null) { dragHit=null;HudLayout.save(); } }
    public static void moveHit(Hit hit,float x,float y) {
        float w=hit.bounds[2],h=hit.bounds[3];
        x=clampPosition(x,w,Render2D.screenWidth());y=clampPosition(y,h,Render2D.screenHeight());
        if(hit.media) Interface.setMediaGeometry(Interface.mediaSize(),(x+w/2)/Render2D.screenWidth()*100,(y+h/2)/Render2D.screenHeight()*100);
        else HudLayout.setPos(hit.element,x,y);
    }
    public static void resizeHit(Hit hit,float factor) {
        if(hit.media) Interface.setMediaGeometry(Interface.mediaSize()*factor,Interface.mediaX(),Interface.mediaY());
        else HudLayout.setScale(hit.element,HudLayout.get(hit.element).scale*factor);
    }
    private static float clampPosition(float p,float size,float screen) { return Math.max(4,Math.min(Math.max(4,screen-size-4),p)); }
    private static float[] position(HudLayout.Element el,HudLayout.Entry e,float w,float h) {
        float sw=Render2D.screenWidth(),sh=Render2D.screenHeight(),s=Render2D.guiScale();
        float x=4*s,y=4*s;
        switch(el) {
            case WATERMARK -> { if("Center".equals(Interface.watermarkPos())) x=(sw-w)/2; else if("Right".equals(Interface.watermarkPos())) x=sw-w-4*s; }
            case KEYBINDS -> { x=4*s;y=30*s; }
            case EFFECTS -> { x=140*s;y=30*s; }
            case STAFF -> { x=4*s;y=100*s; }
            case COOLDOWNS -> { x=140*s;y=100*s; }
            case ARRAYLIST -> { x=sw-w-4*s;y=24*s; }
            case TARGET -> { x=(sw-w)/2+80*s;y=sh/2+40*s; }
            case ARMOR -> { x=(sw-w)/2;y=sh-h-48*s; }
            case HOTBAR -> { x=(sw-w)/2;y=sh-h-4*s; }
            case NOTIFICATIONS -> { x=(sw-w)/2;y=sh-h-80*s; }
            case TOTEMS -> { x=sw-w-4*s;y=sh-h-4*s; }
            case TICKS, PING, FPS, BPS, XYZ -> { x=4*s;y=sh-h-(4+14*(el.ordinal()-HudLayout.Element.TICKS.ordinal()))*s; }
        }
        return new float[]{clampPosition(e.positioned()?e.x:x,w,sw),clampPosition(e.positioned()?e.y:y,h,sh)};
    }
    private static QuickFont sTitle(float s) { return QuickFont.of("bold",7*s); }
    private static QuickFont sBody(float s) { return QuickFont.of("bold",7*s); }
    private static QuickFont sLabel(float s) { return QuickFont.of("medium",7*s); }
    private static void renderAll(GuiGraphicsExtractor gg) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=lastLevel) {
            lastLevel=mc.level;STAFF_SINCE.clear();LISTS.clear();QuickTelemetry.reset();QuickNotifications.clear();healthTarget=null;lastTarget=null;targetShow=0;
        }
        long now=System.nanoTime();delta=lastFrame==0?1/60f:Math.min(0.1f,(now-lastFrame)/1e9f);lastFrame=now;frame++;
        if(!editing()) { stopDrag();QuickElementMenu.close(); }
        else if(dragHit!=null) moveHit(dragHit,(float)Render2D.mouseX()-grabX,(float)Render2D.mouseY()-grabY);
        BOUNDS.clear();dev.hatek.client.ui.render.LiquidBackdrop.request();
        Render2D.begin(gg);
        try {
            for(var el:HudLayout.Element.values()) {
                if(!visible(el)) continue;var entry=HudLayout.get(el);float s=entry.scale*Render2D.guiScale();Sized data=measure(el,s);
                if(data==null || data.w()<=0 || data.h()<=0) continue;
                float[] pos=position(el,entry,data.w(),data.h());BOUNDS.put(el,new float[]{pos[0],pos[1],data.w(),data.h()});
                drawing=el;Render2D.pushTranslate(gg,pos[0],pos[1]);QuickStyle.begin(entry);
                try { draw(gg,el,s,data); } finally { QuickStyle.end();Render2D.popTransform(gg); }
            }
            placeMedia2D(gg);QuickElementMenu.render(gg);
        } finally { drawing=null;Render2D.end(gg); }
    }
    private static Sized measure(HudLayout.Element el,float s) {
        return switch(el) {
            case WATERMARK -> measureWatermark(s);
            case HOTBAR -> measureHotbar(s);
            case TARGET -> measureTarget(s);
            case ARMOR -> measureArmor(s);
            case KEYBINDS -> list(el,"HotKeys","util",keyRows(),s);
            case EFFECTS -> list(el,"Potions","E",effectRows(),s);
            case STAFF -> list(el,"StaffList","A",staffRows(),s);
            case COOLDOWNS -> list(el,"CoolDowns","V",cooldownRows(),s);
            case ARRAYLIST -> list(el,"","",moduleRows(s),s);
            case NOTIFICATIONS -> list(el,"","",notificationRows(),s);
            default -> measurePill(el,s);
        };
    }
    private static void draw(GuiGraphicsExtractor gg,HudLayout.Element el,float s,Sized data) {
        switch(el) {
            case WATERMARK -> drawWatermark(gg,s,(WatermarkData)data);
            case HOTBAR -> drawHotbar(gg,s,(HbData)data);
            case TARGET -> {
                Render2D.pushAlpha(targetShow);
                try { drawTarget(gg,s,(TargetData)data); } finally { Render2D.popAlpha(); }
            }
            case ARMOR -> drawArmor(gg,s,(ArmorData)data);
            case KEYBINDS,EFFECTS,STAFF,COOLDOWNS,ARRAYLIST,NOTIFICATIONS -> drawList(gg,el,s,(ListData)data);
            default -> drawPill(gg,s,(PillData)data);
        }
    }
    private static WatermarkData measureWatermark(float s) {
        Minecraft mc=Minecraft.getInstance();String[] parts={"Quick",mc.player.getName().getString(),"Fps "+mc.getFps(),LocalTime.now().format(CLOCK)};
        float w=12*s+QuickFont.of("quick",8*s).width("N");
        for(String p:parts) w+=8*s+sLabel(s).width("»")+sBody(s).width(p);
        return new WatermarkData(w,16*s,parts);
    }
    private static void drawWatermark(GuiGraphicsExtractor gg,float s,WatermarkData d) {
        QuickStyle.panel(gg,d.w,d.h,s);float x=6*s;QuickFont icon=QuickFont.of("quick",8*s);
        x=icon.draw(gg,"N",x,icon.visualBaseline("N",0,d.h),Style.accent());
        for(int i=0;i<d.parts.length;i++) {
            x+=4*s;x=sLabel(s).draw(gg,"»",x,sLabel(s).visualBaseline("»",0,d.h),0xFFB4B4BC);x+=4*s;
            x=sBody(s).draw(gg,d.parts[i],x,sBody(s).visualBaseline("H",0,d.h),i==0?Style.accent():Style.WHITE);
        }
    }
    private static List<Row> keyRows() {
        List<Row> rows=new ArrayList<>();
        for(Module m:ModuleManager.all()) if(m.isEnabled() && m.keyCode()!=GLFW.GLFW_KEY_UNKNOWN) rows.add(new Row(m.name(),m.name(),"["+m.keyLabel()+"]","",null));
        if(rows.isEmpty() && editing()) rows.add(new Row("preview","Interface","[RSHIFT]","",null));return rows;
    }
    private static List<Row> effectRows() {
        List<Row> rows=new ArrayList<>();
        for(MobEffectInstance e:Minecraft.getInstance().player.getActiveEffects()) {
            if(!e.showIcon()) continue;String roman=ampRoman(e.getAmplifier());String name=effectBaseName(e)+(roman==null?"":" "+roman);
            rows.add(new Row(e.getEffect().value().getDescriptionId(),name,formatDuration(e.getDuration()),"",e));
        }
        if(rows.isEmpty() && editing()) rows.add(new Row("preview","Скорость II","1:30","",null));return rows;
    }
    private static List<Row> staffRows() {
        Minecraft mc=Minecraft.getInstance();List<Row> rows=new ArrayList<>();if(mc.getConnection()==null) return rows;
        Set<String> present=new HashSet<>();
        for(PlayerTeam team:mc.level.getScoreboard().getPlayerTeams()) {
            String rank=(team.getPlayerPrefix().getString()+" "+team.getName()).toLowerCase(Locale.ROOT);
            if(!rank.matches(".*(admin|moder|helper|staff|owner|админ|модер|хелпер|куратор).*")) continue;
            for(String name:team.getPlayers()) {
                if(!name.matches("[A-Za-z0-9_]{3,16}") || !present.add(name)) continue;
                PlayerInfo info=mc.getConnection().getPlayerInfo(name);long since=STAFF_SINCE.computeIfAbsent(name,k->System.nanoTime());
                long seconds=(System.nanoTime()-since)/1000000000L;
                String status=info==null?"Vanish":info.getGameMode()==GameType.SPECTATOR?"GM3":"";
                rows.add(new Row(name,name,(status.isEmpty()?"":status+" ")+String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60),"",info));
            }
        }
        STAFF_SINCE.keySet().retainAll(present);
        if(rows.isEmpty() && editing()) rows.add(new Row("preview","Moderator","00:00","",null));return rows;
    }
    private static List<Row> cooldownRows() {
        Minecraft mc=Minecraft.getInstance();List<Row> rows=new ArrayList<>();
        Object cooldowns=mc.player.getCooldowns();
        if(cooldowns instanceof QuickCooldowns tracked) {
            for(var kv:tracked.quick$remainingTicks().entrySet()) {
                ItemStack item=ItemStack.EMPTY;
                for(int i=0;i<mc.player.getInventory().getContainerSize();i++) { ItemStack stack=mc.player.getInventory().getItem(i);
                    if(!stack.isEmpty() && mc.player.getCooldowns().getCooldownGroup(stack).equals(kv.getKey())) { item=stack;break; } }
                String name=item.isEmpty()?kv.getKey().getPath():item.getHoverName().getString();
                rows.add(new Row(kv.getKey().toString(),name,String.format(Locale.ROOT,"%.1fs",kv.getValue()/20f),"",item));
            }
        }
        if(rows.isEmpty() && editing()) rows.add(new Row("preview","Эндер-жемчуг","12.4s","",new ItemStack(Items.ENDER_PEARL)));return rows;
    }
    private static List<Row> moduleRows(float s) {
        List<Row> rows=new ArrayList<>();
        for(Module m:ModuleManager.all()) if(m.isEnabled() && !(m instanceof Interface)) rows.add(new Row(m.name(),m.name(),"","",null));
        rows.sort(Comparator.comparingDouble((Row r)->sBody(s).width(r.left)).reversed());
        if(rows.isEmpty() && editing()) rows.add(new Row("preview","Interface","","",null));return rows;
    }
    private static List<Row> notificationRows() {
        List<Row> rows=new ArrayList<>();
        for(var n:QuickNotifications.current()) rows.add(new Row("notice-"+n.created(),n.text(),"",n.enabled()?"J":"K",null));
        if(rows.isEmpty() && editing()) rows.add(new Row("preview","Пример уведомления","","J",null));return rows;
    }
    private static ListData list(HudLayout.Element el,String title,String icon,List<Row> source,float s) {
        ListAnimation state=LISTS.computeIfAbsent(el,k->new ListAnimation());
        if(state.seen!=frame) {
            state.seen=frame;Set<String> active=new HashSet<>();
            for(Row row:source) { active.add(row.key);state.rows.computeIfAbsent(row.key,k->new RowAnimation(row)).row=row; }
            float k=1-(float)Math.exp(-14*delta);
            for(var kv:state.rows.entrySet()) { RowAnimation a=kv.getValue();a.alpha+=((active.contains(kv.getKey())?1:0)-a.alpha)*k; }
            state.rows.values().removeIf(a->a.alpha<0.01f && !active.contains(a.row.key));
        }
        List<RowAnimation> rows=new ArrayList<>(state.rows.values());
        if(el==HudLayout.Element.ARRAYLIST) rows.sort(Comparator.comparingDouble((RowAnimation a)->sBody(s).width(a.row.left)).reversed());
        if(rows.isEmpty() && !editing()) return null;
        boolean separate=title.isEmpty();float rowH=(el==HudLayout.Element.NOTIFICATIONS?17:separate?14:11)*s;
        float w=separate?40*s:86*s,h=separate?0:16*s;
        for(var row:rows) { boolean sprite=row.row.sprite!=null;
            float rw=(sprite?24:12)*s+sBody(s).width(row.row.left)+sBody(s).width(row.row.right)+(row.row.right.isEmpty()?0:10*s);
            if(!row.row.icon.isEmpty()) rw+=12*s;
            w=Math.max(w,rw);h+=rowH*row.alpha;
        }
        if(!separate && !rows.isEmpty()) h+=3*s*Math.min(1,rows.stream().mapToDouble(a->a.alpha).sum());
        if(state.w==0 || Math.abs(s-state.scale)>0.01f) { state.w=w;state.h=h;state.scale=s; }
        else if(state.dimensions!=frame) { float k=1-(float)Math.exp(-18*delta);state.w+=(w-state.w)*k;state.h+=(h-state.h)*k; }
        state.dimensions=frame;
        return new ListData(state.w,Math.max(1,state.h),title,icon,rows);
    }
    private static void drawList(GuiGraphicsExtractor gg,HudLayout.Element el,float s,ListData d) {
        boolean separate=d.title.isEmpty();float y=0;
        if(!separate) {
            QuickStyle.panel(gg,d.w,d.h,s);QuickStyle.text(gg,d.title,6*s,0,16*s,s,Style.WHITE);
            if("util".equals(d.icon)) Render2D.texture(gg,QuickAssets.texture("util"),d.w-15*s,3.5f*s,9*s,9*s,Style.accent());
            else { QuickFont f=QuickFont.of("A".equals(d.icon)?"quick":"icons_nurik",8*s);
                f.drawRight(gg,d.icon,d.w-6*s,f.visualBaseline(d.icon,0,16*s),Style.accent()); }
            Render2D.rect(gg,6*s,16*s,d.w-12*s,Math.max(1,s*0.35f),0x24FFFFFF);y=17.5f*s;
        }
        Render2D.pushClip(gg,separate?-8*s:0,separate?-8*s:16*s,d.w+(separate?16*s:0),d.h+(separate?16*s:-16*s));
        try {
            for(var a:d.rows) {
                Row row=a.row;float rowH=(el==HudLayout.Element.NOTIFICATIONS?17:separate?14:11)*s;
                Render2D.pushAlpha(a.alpha);
                float x=6*s;
                if(separate) {
                    float rw=el==HudLayout.Element.ARRAYLIST?sBody(s).width(row.left)+12*s:d.w;
                    boolean right=el==HudLayout.Element.ARRAYLIST && BOUNDS.get(el)[0]>Render2D.screenWidth()/2f;
                    float rx=right?d.w-rw:0;Render2D.pushTranslate(gg,rx,y);
                    QuickStyle.panel(gg,rw,rowH-2*s,s);
                    if(!row.icon.isEmpty()) { QuickFont f=QuickFont.of("icons_nurik",8*s);f.draw(gg,row.icon,4*s,f.visualBaseline(row.icon,0,rowH-2*s),Style.accent());x=16*s; }
                    QuickStyle.text(gg,row.left,x,0,rowH-2*s,s,el==HudLayout.Element.ARRAYLIST?Style.accent():Style.WHITE);
                    Render2D.popTransform(gg);
                } else {
                    if(row.sprite instanceof MobEffectInstance effect) {
                        gg.blitSprite(RenderPipelines.GUI_TEXTURED,Hud.getMobEffectSprite(effect.getEffect()),Math.round(x),Math.round(y+s),Math.round(9*s),Math.round(9*s),Render2D.withAlpha(0xFFFFFFFF,Render2D.alpha()));x+=13*s;
                    } else if(row.sprite instanceof PlayerInfo player) { drawHead(gg,player,x,y+1.5f*s,8*s);x+=12*s; }
                    else if(row.sprite instanceof ItemStack item) { item(gg,item,x,y,10*s);x+=14*s; }
                    QuickStyle.text(gg,row.left,x,y,rowH,s,Style.WHITE);
                    int color=row.sprite instanceof MobEffectInstance effect && effect.getDuration()>=0 && effect.getDuration()<200?0xFFEB3C46:0xFFCACAD0;
                    sBody(s).drawRight(gg,row.right,d.w-6*s,sBody(s).visualBaseline("H",y,rowH),color);
                }
                Render2D.popAlpha();y+=rowH*a.alpha;
            }
        } finally { Render2D.popClip(gg); }
    }
    private static PillData measurePill(HudLayout.Element el,float s) {
        Minecraft mc=Minecraft.getInstance();String text;ItemStack item=ItemStack.EMPTY;
        switch(el) {
            case TICKS -> text=String.format(Locale.ROOT,"%.2f Ticks",mc.hasSingleplayerServer()?20:QuickTelemetry.tps());
            case PING -> { PlayerInfo info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(mc.player.getUUID()); text=(info==null?0:info.getLatency())+" Ping"; }
            case FPS -> text=mc.getFps()+" FPS";
            case BPS -> text=String.format(Locale.ROOT,"%.2f BPS",Math.hypot(mc.player.getX()-mc.player.xo,mc.player.getZ()-mc.player.zo)*20);
            case XYZ -> text=String.format(Locale.ROOT,"%.1f, %.1f, %.1f XYZ",mc.player.getX(),mc.player.getY(),mc.player.getZ());
            case TOTEMS -> { int count=0;for(int i=0;i<mc.player.getInventory().getContainerSize();i++) { ItemStack stack=mc.player.getInventory().getItem(i);if(stack.is(Items.TOTEM_OF_UNDYING)) count+=stack.getCount(); }
                // Offhand is part of the inventory container on this Minecraft version.
                text=String.valueOf(count);item=new ItemStack(Items.TOTEM_OF_UNDYING); }
            default -> { return null; }
        }
        return new PillData(12*s+sBody(s).width(text)+(item.isEmpty()?0:14*s),16*s,text,item);
    }
    private static void drawPill(GuiGraphicsExtractor gg,float s,PillData d) {
        QuickStyle.panel(gg,d.w,d.h,s);float x=6*s;
        if(!d.item.isEmpty()) { item(gg,d.item,4*s,1.5f*s,13*s);x+=14*s; }
        QuickStyle.text(gg,d.text,x,0,d.h,s,Style.WHITE);
    }
    private static ArmorData measureArmor(float s) {
        Minecraft mc=Minecraft.getInstance();List<ItemStack> items=new ArrayList<>();
        items.add(mc.player.getMainHandItem());items.addAll(armorPieces(mc.player));items.add(mc.player.getOffhandItem());
        if(items.stream().allMatch(ItemStack::isEmpty) && !editing()) return null;
        float longSide=99*s,shortSide=19*s;HudLayout.Entry entry=HudLayout.get(HudLayout.Element.ARMOR);
        float x=entry.positioned()?entry.x:(Render2D.screenWidth()-longSide)/2;
        boolean vertical=x<longSide+3*s || x>Render2D.screenWidth()-longSide-3*s;
        return new ArmorData(vertical?shortSide:longSide,vertical?longSide:shortSide,items,vertical);
    }
    private static void drawArmor(GuiGraphicsExtractor gg,float s,ArmorData d) {
        QuickStyle.panel(gg,d.w,d.h,s);
        for(int i=0;i<d.items.size();i++) { float x=3*s+(d.vertical?0:i*16*s),y=3*s+(d.vertical?i*16*s:0);
            ItemStack item=d.items.get(i);item(gg,item,x,y,13*s);
            if(item.isDamageableItem()) Render2D.round(gg,x,y+11*s,13*s*durabilityFrac(item),2*s,s,durabilityColor(item)); }
    }
    private static TargetData measureTarget(float s) {
        Minecraft mc=Minecraft.getInstance();LivingEntity current=editing()?mc.player:resolveTarget(mc);
        if(targetFrame!=frame) {
            targetFrame=frame;
            if(current!=null) lastTarget=current;
            targetShow+=((current!=null?1:0)-targetShow)*(1-(float)Math.exp(-15*delta));
        }
        LivingEntity t=current!=null?current:lastTarget;if(t==null || targetShow<0.01f) return null;
        float max=Math.max(1,t.getMaxHealth());return new TargetData(112*s,30*s,t,QuickFont.of("bold",6.5f*s).trim(t.getName().getString(),42*s),
                String.format(Locale.ROOT,"%.1f HP",t.getHealth()+t.getAbsorptionAmount()),mc.player.distanceTo(t),
                Mth.clamp(t.getHealth()/max,0,1),Mth.clamp(t.getAbsorptionAmount()/max,0,1));
    }
    private static void drawTarget(GuiGraphicsExtractor gg,float s,TargetData d) {
        if(healthTarget!=d.target) { healthTarget=d.target;healthAnimation=d.health;damageAnimation=d.health; }
        healthAnimation+=(d.health-healthAnimation)*(1-(float)Math.exp(-18*delta));
        damageAnimation+=(d.health-damageAnimation)*(1-(float)Math.exp(-5*delta));
        QuickStyle.panel(gg,d.w,d.h,s);Minecraft mc=Minecraft.getInstance();
        PlayerInfo info=d.target instanceof Player player && mc.getConnection()!=null?mc.getConnection().getPlayerInfo(player.getUUID()):null;
        if(info!=null) drawHead(gg,info,4.5f*s,4.5f*s,21*s);
        else { Render2D.round(gg,4.5f*s,4.5f*s,21*s,21*s,3*s,0xFF25252E);sBody(s).drawCentered(gg,d.name.isEmpty()?"?":d.name.substring(0,1),15*s,19*s,Style.accent()); }
        float x=31*s,w=76*s;QuickFont name=QuickFont.of("bold",6.5f*s),small=QuickFont.of("bold",6*s);
        name.draw(gg,d.name,x,10*s,Style.WHITE);small.drawRight(gg,d.hp,d.w-5*s,10*s,Style.accent());
        Render2D.round(gg,x,14.5f*s,w,3.5f*s,1.75f*s,0x24FFFFFF);
        Render2D.round(gg,x,14.5f*s,w*damageAnimation,3.5f*s,1.75f*s,Render2D.withAlpha(Style.accent(),0.45f));
        Render2D.round(gg,x,14.5f*s,w*healthAnimation,3.5f*s,1.75f*s,Style.accent());
        if(d.absorption>0) Render2D.round(gg,x,14.5f*s,w*d.absorption,3.5f*s,1.75f*s,0xFFFFD45F);
        QuickFont.of("medium",5.5f*s).draw(gg,String.format(Locale.ROOT,"%.1fm",d.dist),x,25*s,0xFFD0D0D7);
        item(gg,d.target.getMainHandItem(),d.w-15*s,19*s,8*s);
    }
    private static void drawHead(GuiGraphicsExtractor gg,PlayerInfo info,float x,float y,float d) {
        Identifier id=info.getSkin().body().texturePath();
        int px=Math.round(x),py=Math.round(y),size=Math.round(d),color=Render2D.withAlpha(0xFFFFFFFF,Render2D.alpha());
        gg.blit(RenderPipelines.GUI_TEXTURED,id,px,py,8,8,size,size,8,8,64,64,color);
        if(info.showHat()) gg.blit(RenderPipelines.GUI_TEXTURED,id,px,py,40,8,size,size,8,8,64,64,color);
    }
    private static void item(GuiGraphicsExtractor gg,ItemStack stack,float x,float y,float d) {
        if(stack.isEmpty()) return;
        Render2D.pushTranslate(gg,x,y);Render2D.pushScale(gg,0,0,d/16);
        gg.item(Minecraft.getInstance().player,stack,0,0,0);Render2D.popTransform(gg);Render2D.popTransform(gg);
    }
    private static void sheet(GuiGraphicsExtractor gg,float w,float h,float s) { QuickStyle.panel(gg,w,h,s); }
    private static void slot(GuiGraphicsExtractor gg,Minecraft mc,ItemStack stack,float x,float y,float d,float s) {
        Render2D.round(gg,x,y,d,d,3*s,0x28282835);item(gg,stack,x+2*s,y+2*s,d-4*s);
    }
    private static void frame(GuiGraphicsExtractor gg,float x,float y,float d,int color) {
        Render2D.round(gg,x,y,d,d,3,Render2D.withAlpha(color,0.3f));
    }
    private static void thinBar(GuiGraphicsExtractor gg,float x,float y,float w,float frac,int color) {
        Render2D.rect(gg,x,y,w,1,0x22FFFFFF);Render2D.rect(gg,x,y,w*frac,1,color);
    }

    private static final Map<String, String> EFFECT_NAMES = new java.util.HashMap<>();

    /** Имя эффекта без уровня, резолвится через translation-систему — кэшируем. */
    private static String effectBaseName(MobEffectInstance instance) {
        String key = instance.getEffect().value().getDescriptionId();
        String cached = EFFECT_NAMES.get(key);
        if (cached != null) {
            return cached;
        }
        String name = Component.translatable(
                instance.getEffect().value().getDescriptionId()).getString();
        if (EFFECT_NAMES.size() < 256) {
            EFFECT_NAMES.put(key, name);
        }
        return name;
    }

    /** Римская цифра уровня (null — первый уровень, бейдж не нужен). */
    private static String ampRoman(int amp) {
        if (amp <= 0) {
            return null;
        }
        return toRoman(amp + 1);
    }

    private static String formatDuration(int ticks) {
        if (ticks < 0 || ticks > 1_000_000) {
            return "∞";
        }
        int total = ticks / 20;
        int mm = total / 60;
        int ss = total % 60;
        return mm + ":" + (ss < 10 ? "0" : "") + ss;
    }

    private static String toRoman(int n) {
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 1 && n <= 10) {
            return romans[n - 1];
        }
        return String.valueOf(n);
    }

    // ---------------- кастомный хотбар ----------------


    private static final class HbData implements Sized {
        final float w;
        final float h;
        final float s; // эффективный масштаб (со слайдером)

        HbData(float w, float h, float s) {
            this.w = w;
            this.h = h;
            this.s = s;
        }

        @Override
        public float w() {
            return this.w;
        }

        @Override
        public float h() {
            return this.h;
        }
    }

    private static HbData measureHotbar(float scale) {
        float s = scale * (float) Interface.hotbarSize();
        float slot = 22.0f * s;
        float gap = 2.0f * s;
        float extra=Minecraft.getInstance().player.getOffhandItem().isEmpty()?0:slot+6*s;
        return new HbData(slot * 9.0f + gap * 8.0f + extra, slot, s);
    }

    /** Рисует хотбар в origin (0,0) с ручным масштабом. */
    private static void drawHotbar(GuiGraphicsExtractor gg, float ignored, HbData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();
        float s = data.s;

        float slot = 22.0f * s;
        float gap = 2.0f * s;

        float offset=mc.player.getOffhandItem().isEmpty()?0:slot+6*s;
        for (int i = 0; i < 9; i++) {
            float sx = offset + i * (slot + gap);
            boolean selected = i == inv.getSelectedSlot();
            slot(gg, mc, inv.getItem(i), sx, 0, slot, s);
            if (selected) {
                // Акцентная рамка + подчёркивание 2px.
                frame(gg, sx, 0, slot, Style.accent());
                Render2D.rect(gg, sx, slot - 2.0f * s, slot, 2.0f * s,
                        Style.accent());
            }
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            slot(gg, mc, offhand, 0, 0, slot, s);
        }
    }

    // ---------------- броня (своя) ----------------


    private static List<ItemStack> armorPieces(LivingEntity living) {
        List<ItemStack> out = new ArrayList<>(4);
        out.add(living.getItemBySlot(EquipmentSlot.HEAD));
        out.add(living.getItemBySlot(EquipmentSlot.CHEST));
        out.add(living.getItemBySlot(EquipmentSlot.LEGS));
        out.add(living.getItemBySlot(EquipmentSlot.FEET));
        return out;
    }

    private static String durabilityPct(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (stack.isEmpty() || max <= 0) {
            return null;
        }
        int pct = Math.round((max - stack.getDamageValue()) / (float) max * 100.0f);
        return pct + "%";
    }

    /** Доля оставшейся прочности (для баров). */
    private static float durabilityFrac(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (stack.isEmpty() || max <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f,
                (max - stack.getDamageValue()) / (float) max));
    }

    private static int durabilityColor(ItemStack stack) {
        int max = stack.getMaxDamage();
        float f = max <= 0 ? 1.0f : (max - stack.getDamageValue()) / (float) max;
        return f > 0.6f ? 0xFF4ADE80 : f > 0.3f ? 0xFFFACC15 : 0xFFF87171;
    }

    // ---------------- цель ----------------


    private static LivingEntity resolveTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        LivingEntity aura = AttackAura.target;
        if (isValidHudTarget(mc, aura)) {
            return aura;
        }
        return raycastTarget(mc);
    }

    private static boolean isValidHudTarget(Minecraft mc, LivingEntity e) {
        return e != null && e != mc.player && e.isAlive() && !e.isRemoved()
                && e.distanceTo(mc.player) < 16.0f;
    }

    private static LivingEntity raycastTarget(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        if (cam == null) {
            return null;
        }
        Vec3 eye = cam.getEyePosition();
        Vec3 look = cam.getLookAngle();
        double range = 6.0;
        Vec3 end = eye.add(look.scale(range));
        LivingEntity best = null;
        double bestD = range;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || !isValidHudTarget(mc, living)) {
                continue;
            }
            Optional<Vec3> clip = living.getBoundingBox().inflate(0.35).clip(eye, end);
            if (clip.isEmpty()) {
                continue;
            }
            double d = eye.distanceTo(clip.get());
            if (d < bestD) {
                bestD = d;
                best = living;
            }
        }
        return best;
    }

    // ---------------- медиа 2D ----------------

    /**
     * Границы 2D-медиа в пикселях экрана {x, y, w, h} или null.
     * Позиция/размер берутся из настроек (проценты/пиксели).
     */

    public static float[] media2DBounds() {
        MediaClip clip = MediaManager.get(Interface.mediaFile());
        if (clip == null) {
            return null;
        }
        MediaClip.Frame frame = clip.frameAt(System.currentTimeMillis());
        float h = (float) Interface.mediaSize();
        float w = h * frame.width() / (float) frame.height();
        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();
        float x = sw * (float) Interface.mediaX() / 100.0f - w / 2.0f;
        float y = sh * (float) Interface.mediaY() / 100.0f - h / 2.0f;
        return new float[]{x, y, w, h};
    }

    private static void placeMedia2D(GuiGraphicsExtractor gg) {
        if (!Interface.showMedia2D()) {
            return;
        }
        float[] b = media2DBounds();
        if (b == null) {
            return;
        }
        MediaClip clip = MediaManager.get(Interface.mediaFile());
        MediaClip.Frame frame = clip.frameAt(System.currentTimeMillis());
        Render2D.texture(gg, frame.texture(), b[0], b[1], b[2], b[3], Style.WHITE);
    }
}
