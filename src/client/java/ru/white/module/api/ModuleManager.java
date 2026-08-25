package ru.white.module.api;

import ru.white.Client;

import ru.white.manager.event_impl.EventKey;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.impl.combat.*;
import ru.white.module.impl.combat.*;
import ru.white.module.impl.display.Arrows;
import ru.white.module.impl.display.ClickGui;
import ru.white.module.impl.display.Emotions;
import ru.white.module.impl.display.Hud;

import ru.white.module.impl.display.InterFace;
import ru.white.module.impl.movement.*;
import ru.white.module.impl.player.*;
import ru.white.module.impl.render.*;
import ru.white.module.impl.utils.*;

import ru.white.module.impl.movement.*;
import ru.white.module.impl.player.*;
import ru.white.module.impl.render.*;
import ru.white.module.impl.utils.*;


import java.util.*;
import java.util.stream.Collectors;


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
                new AppleFarmer()

                ,
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
        // Обычный цикл вместо stream: обработчик клавиш дёргается на каждое нажатие
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
        // Карта уже ключуется классом модуля — прямой поиск вместо линейного скана
        Module direct = super.get(clazz);
        if (direct != null) return clazz.cast(direct);

        // Запрос по суперклассу/интерфейсу: обход в том же (отсортированном)
        // порядке, что и раньше, — выбирается тот же модуль
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

    // ── кэш отсортированного списка и поиска по имени ─────────────────────
    // values() вызывался по несколько раз за кадр (ClickGui, HUD-элементы),
    // каждый раз пересобирая и пересортировывая список из ~80 модулей
    // компаратором CASE_INSENSITIVE_ORDER. Состав модулей меняется только
    // при init/unregister, поэтому результат кэшируется.

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
            // Обход в том же (отсортированном) порядке, что и раньше у values():
            // при совпадении имён побеждает тот же модуль, что и до кэширования
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
            // Только для чтения: список переиспользуется между кадрами, случайная
            // правка снаружи испортила бы кэш
            cached = Collections.unmodifiableList(sorted);
            sortedCache = cached;
        }
        return cached;
    }
}
