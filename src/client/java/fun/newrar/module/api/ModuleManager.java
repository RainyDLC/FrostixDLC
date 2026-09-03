package fun.newrar.module.api;

import fun.newrar.Client;
import fun.newrar.manager.event_impl.EventKey;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.impl.combat.*;
import fun.newrar.module.impl.display.Arrows;
import fun.newrar.module.impl.display.ClickGui;
import fun.newrar.module.impl.display.Emotions;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.module.impl.movement.*;
import fun.newrar.module.impl.player.*;
import fun.newrar.module.impl.render.*;
import fun.newrar.module.impl.utils.*;

import java.util.*;

public final class ModuleManager extends LinkedHashMap<Class<? extends Module>, Module> {
    public void init() {
        addSorted(
                new AttackAura(),
                new AutoSwap(),
                new AutoTotem(),
                new AimBot(),
                new ProjectileAimBot(),
                new NoFriendDamage(),
                new NoSlotChange(),
                new HitBoxes(),
                new Criticals(),
                new CrystalAura(),
                new MaceHelper(),
                new AntiBot(),
                new TriggerBot(),
                new TpAura(),
                new UseTracker(),

                new Sprint(),
                new NoSlow(),
                new Spider(),
                new NoWeb(),
                new Speed(),
                new WaterSpeed(),
                new AirStuck(),
                new Fly(),
                new ElytraSpeed(),

                new ClickGui(),
                new Arrows(),
                new EntityEsp(),
                new NameTag(),
                new NoRender(),
                new SwingAnimation(),
                new GlassHands(),
                new GlassBlock(),
                new ShaderEsp(),
                new ShaderSky(),
                new WorldTweaks(),
                new Gamma(),
                new Particles(),
                new HealthAlert(),
                new BlockEsp(),
                new ChinaHat(),
                new JumpCircle(),
                new JumpCube(),
                new CrossHair(),
                new Trails(),
                new PenisEsp(),
                new WorldCubes(),
                new Trajectories(),
                new ColorGrade(),
                new FireFlies(),
                new Svetoch(),
                new ScanWorld(),
                new FogBlur(),
                new TotemGhost(),
                new KillEffect(),
                new Hands(),
                new InterFace(),
                new ReportHelper(),
                new Emotions(),
                new AutoAccept(),
                new NoPush(),
                new NoDelay(),
                new ClickHelper(),
                new WorldTracker(),
                new InventoryMove(),
                new ElytraHelper(),
                new AutoLeave(),
                new AutoTool(),
                new AuctionHelper(),
                new LockSlot(),
                new FreeCamera(),
                new FreeLook(),
                new PvpSafe(),
                new AppleFarmer(),

                new UnHook(),
                new NameProtect(),
                new GlassFarmer(),
                new PotionFarmer(),
                new SPJoiner(),
                new FakePlayer(),
                new ChestStealer(),
                new AutoClanUpgrade(),
                new AutoStorage(),
                new WardenHelper(),
                new AutoWarden(),
                new AuraCrafter(),
                new AutoResell(),
                new ItemScroller(),
                new AutoDuel(),
                new AutoPotion(),
                new DanjHelper(),
                new AutoInvest()
        );

        this.values().stream()
                .filter(Module::isAutoEnabled)
                .forEach(module -> module.setEnabled(true, false));

        Client.eventHandler().subscribe(this);
    }

    public void addSorted(Module... modules) {
        for (Module module : modules) {
            this.put(module.getClass(), module);
        }
    }

    public void unregister(Module... modules) {
        for (Module module : modules) {
            this.remove(module.getClass());
        }
    }

    @EventHandler
    public void onKeyboardPress(EventKey event) {
        for (Module module : values()) {
            if (module.getKey() == event.getKey()) {
                module.toggle();
            }
        }
    }

    public <T extends Module> T get(final String name) {
        return (T) getModule(name);
    }

    public <T extends Module> T get(final Class<T> clazz) {
        Module direct = super.get(clazz);
        if (direct != null) return clazz.cast(direct);

        for (Module module : values()) {
            if (clazz.isAssignableFrom(module.getClass())) return clazz.cast(module);
        }
        return null;
    }

    public List<Module> get(final Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : values()) {
            if (module.getCategory() == category) result.add(module);
        }
        return result;
    }

    public Module getModule(String name) {
        return byName().get(name.toLowerCase(Locale.ROOT));
    }

    private transient List<Module> sortedCache;
    private transient Map<String, Module> nameCache;

    private void invalidateCaches() {
        sortedCache = null;
        nameCache = null;
    }

    @Override
    public Module put(Class<? extends Module> key, Module value) {
        invalidateCaches();
        return super.put(key, value);
    }

    @Override
    public Module remove(Object key) {
        invalidateCaches();
        return super.remove(key);
    }

    @Override
    public void clear() {
        invalidateCaches();
        super.clear();
    }

    private Map<String, Module> byName() {
        Map<String, Module> cached = nameCache;
        if (cached == null) {
            cached = new HashMap<>();
            for (Module module : values()) {
                cached.putIfAbsent(module.getName().toLowerCase(Locale.ROOT), module);
            }
            nameCache = cached;
        }
        return cached;
    }

    @Override
    public Collection<Module> values() {
        List<Module> cached = sortedCache;
        if (cached == null) {
            List<Module> sorted = new ArrayList<>(super.values());
            sorted.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
            cached = Collections.unmodifiableList(sorted);
            sortedCache = cached;
        }
        return cached;
    }
}

