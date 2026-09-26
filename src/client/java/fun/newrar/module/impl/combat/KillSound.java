package fun.newrar.module.impl.combat;

import fun.newrar.manager.event_impl.AttackEvent;
import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.notification.NotificationManager;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.other.SoundUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@ModuleInfo(
        name = "Kill Sound",
        desc = "Воспроизведение эпичной женской озвучки убийств (Rampage, Holy Shit, Monster Kill и др.)",
        category = Category.COMBAT
)
public class KillSound extends Module {
    public static KillSound getInstance() {
        return Instance.get(KillSound.class);
    }

    public ModeSetting voicePack = new ModeSetting(this, "Голос", "Dota 2 (Женский)", "Классический (Мужской)", "Микс (Mix)");
    public ModeSetting streakMode = new ModeSetting(this, "Режим серии",
            "Прогрессивный",
            "Только Rampage",
            "Только Monster Kill",
            "Только Holy Shit",
            "Только Wicked Sick",
            "Только Godlike",
            "Случайный хаос"
    );
    public SliderSetting volume = new SliderSetting(this, "Громкость", 1.0F, 0.1F, 1.0F, 0.05F);
    public SliderSetting streakTimeout = new SliderSetting(this, "Таймаут серии", 15.0F, 3.0F, 60.0F, 1.0F);
    public BooleanSetting onlyPlayers = new BooleanSetting(this, "Только игроки", true);
    public BooleanSetting resetOnDeath = new BooleanSetting(this, "Сброс при смерти", true);
    public BooleanSetting evilLaugh = new BooleanSetting(this, "Злой смех на сериях", true);
    public BooleanSetting hudNotification = new BooleanSetting(this, "Уведомление", true);
    public BooleanSetting chatNotification = new BooleanSetting(this, "Сообщение в чат", false);

    public ButtonSetting testNext = new ButtonSetting(this, "Тест серии", this::playTestNext);
    public ButtonSetting testRampage = new ButtonSetting(this, "Тест Rampage (Жен)", () -> playDirectSound("female_rampage", "RAMPAGE!"));
    public ButtonSetting testMonsterKill = new ButtonSetting(this, "Тест Monster Kill (Жен)", () -> playDirectSound("female_monsterkill", "MONSTER KILL!"));
    public ButtonSetting testHolyShit = new ButtonSetting(this, "Тест Holy Shit (Жен)", () -> playDirectSound("female_holyshit", "HOLY SHIT!"));
    public ButtonSetting testWickedSick = new ButtonSetting(this, "Тест Wicked Sick (Жен)", () -> playDirectSound("female_wickedsick", "WICKED SICK!"));
    public ButtonSetting testGodlike = new ButtonSetting(this, "Тест Godlike (Жен)", () -> playDirectSound("female_godlike", "GODLIKE!"));
    public ButtonSetting testFirstBlood = new ButtonSetting(this, "Тест First Blood (Жен)", () -> playDirectSound("female_firstblood", "FIRST BLOOD!"));
    public ButtonSetting testVictory = new ButtonSetting(this, "Тест Victory (Жен)", () -> playDirectSound("female_victory", "YOU ARE VICTORIOUS!"));

    private final Map<Integer, Long> recentTargets = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private int streak = 0;
    private long lastKillTime = 0L;
    private int lastKilledId = -1;
    private int testStreak = 0;

    private static final byte DEATH_STATUS = 3;
    private static final long ATTACK_WINDOW_MS = 6500L;
    private static final long DUPLICATE_KILL_WINDOW_MS = 800L;

    private static final String[] FEMALE_CHAOS_SOUNDS = {
            "female_firstblood",
            "female_doublekill",
            "female_triplekill",
            "female_megakill",
            "female_ultrakill",
            "female_rampage",
            "female_killingspree",
            "female_dominating",
            "female_unstoppable",
            "female_wickedsick",
            "female_monsterkill",
            "female_godlike",
            "female_holyshit",
            "female_ownage",
            "female_victory"
    };

    private static final String[] FEMALE_CHAOS_TITLES = {
            "FIRST BLOOD!",
            "DOUBLE KILL!",
            "TRIPLE KILL!",
            "MEGA KILL!",
            "ULTRA KILL!",
            "RAMPAGE!",
            "KILLING SPREE!",
            "DOMINATING!",
            "UNSTOPPABLE!",
            "WICKED SICK!",
            "MONSTER KILL!",
            "GODLIKE!",
            "HOLY SHIT!",
            "OWNAGE!",
            "VICTORY!"
    };

    private static final String[] FEMALE_LAUGHS = {"laughs2", "laughs3", "laughs4"};

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        resetState();
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (mc.player == null) return;
        if (!(e.getTarget() instanceof LivingEntity living) || living == mc.player) return;
        if (onlyPlayers.getValue() && !(living instanceof PlayerEntity)) return;

        recentTargets.put(living.getId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (mc.world == null || mc.player == null) return;
        if (!(e.getPacket() instanceof EntityStatusS2CPacket packet)) return;

        if (packet.getStatus() == DEATH_STATUS) {
            Entity entity = packet.getEntity(mc.world);
            if (entity instanceof LivingEntity living) {
                Long attackTime = recentTargets.remove(living.getId());
                if (attackTime != null && System.currentTimeMillis() - attackTime <= ATTACK_WINDOW_MS) {
                    registerKill(living);
                }
            }
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        if (resetOnDeath.getValue() && (!mc.player.isAlive() || mc.player.getHealth() <= 0.0F)) {
            streak = 0;
            recentTargets.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long timeoutMs = (long) (streakTimeout.getValue() * 1000L);
        if (streak > 0 && now - lastKillTime > timeoutMs) {
            streak = 0;
        }

        if (AttackAura.target != null && AttackAura.target != mc.player) {
            if (!onlyPlayers.getValue() || AttackAura.target instanceof PlayerEntity) {
                recentTargets.put(AttackAura.target.getId(), now);
            }
        }

        recentTargets.entrySet().removeIf(entry -> {
            int entityId = entry.getKey();
            long attackTime = entry.getValue();

            if (now - attackTime > ATTACK_WINDOW_MS) {
                return true;
            }

            Entity entity = mc.world.getEntityById(entityId);
            if (entity instanceof LivingEntity living) {
                if (!living.isAlive() || living.isRemoved() || living.getHealth() <= 0.0F) {
                    registerKill(living);
                    return true;
                }
            } else if (entity == null) {
                return true;
            }

            return false;
        });
    }

    private void registerKill(LivingEntity victim) {
        if (victim == null || victim == mc.player) return;
        if (onlyPlayers.getValue() && !(victim instanceof PlayerEntity)) return;

        long now = System.currentTimeMillis();
        if (victim.getId() == lastKilledId && now - lastKillTime < DUPLICATE_KILL_WINDOW_MS) {
            return;
        }

        long timeoutMs = (long) (streakTimeout.getValue() * 1000L);
        if (streak > 0 && now - lastKillTime > timeoutMs) {
            streak = 0;
        }

        streak++;
        lastKillTime = now;
        lastKilledId = victim.getId();

        playSoundAndNotify(streak, victim.getName().getString());
    }

    private void playSoundAndNotify(int currentStreak, String victimName) {
        String soundFile;
        String title;

        boolean preferFemale = !voicePack.is("Классический (Мужской)");
        boolean mix = voicePack.is("Микс (Mix)");

        if (streakMode.is("Только Rampage")) {
            soundFile = preferFemale ? "female_rampage" : "rampage";
            title = "RAMPAGE!";
        } else if (streakMode.is("Только Monster Kill")) {
            soundFile = preferFemale ? "female_monsterkill" : "monsterkill";
            title = "MONSTER KILL!";
        } else if (streakMode.is("Только Holy Shit")) {
            soundFile = preferFemale ? "female_holyshit" : "holyshit";
            title = "HOLY SHIT!";
        } else if (streakMode.is("Только Wicked Sick")) {
            soundFile = preferFemale ? "female_wickedsick" : "whickedsick";
            title = "WICKED SICK!";
        } else if (streakMode.is("Только Godlike")) {
            soundFile = preferFemale ? "female_godlike" : "godlike";
            title = "GODLIKE!";
        } else if (streakMode.is("Случайный хаос")) {
            if (preferFemale && (!mix || random.nextBoolean())) {
                int idx = random.nextInt(FEMALE_CHAOS_SOUNDS.length);
                soundFile = FEMALE_CHAOS_SOUNDS[idx];
                title = FEMALE_CHAOS_TITLES[idx];
            } else {
                String[] malePool = {"firstblood", "doublekill", "triplekill", "quadrakill", "pentakill", "hexakill",
                        "megakill", "ultrakill", "rampage", "killingspree", "monsterkill", "dominating", "unstoppable",
                        "godlike", "whickedsick", "holyshit", "ludicrouskill", "killingmachine", "massacre", "unreal"};
                int idx = random.nextInt(malePool.length);
                soundFile = malePool[idx];
                title = soundFile.toUpperCase() + "!";
            }
        } else {
            // Progressive streak
            SoundStage stage = getProgressiveStage(currentStreak, preferFemale, mix);
            soundFile = stage.sound;
            title = stage.title;
        }

        SoundUtil.playSound_wav("killsound/" + soundFile, volume.getValue());

        // Evil laugh on major hype milestones
        if (evilLaugh.getValue() && (currentStreak == 7 || currentStreak == 9 || currentStreak == 12 || currentStreak == 14 || currentStreak % 10 == 0)) {
            CompletableFuture.delayedExecutor(1250, TimeUnit.MILLISECONDS).execute(() -> {
                if (this.isEnabled()) {
                    String laugh = preferFemale ? FEMALE_LAUGHS[random.nextInt(FEMALE_LAUGHS.length)] : "laughs";
                    SoundUtil.playSound_wav("killsound/" + laugh, volume.getValue());
                }
            });
        }

        if (hudNotification.getValue()) {
            NotificationManager.send(title + " (Серия: " + currentStreak + ")", NotificationManager.Type.INFO, 2500);
        }

        if (chatNotification.getValue() && mc.player != null) {
            String vName = (victimName != null && !victimName.isEmpty()) ? victimName : "Враг";
            mc.player.sendMessage(Text.literal("§d[KillSound] §f" + title + " §7(Жертва: §c" + vName + "§7, Серия: §e" + currentStreak + "§7)"), false);
        }
    }

    private record SoundStage(String sound, String title) {}

    private SoundStage getProgressiveStage(int currentStreak, boolean preferFemale, boolean mix) {
        boolean useFemale = preferFemale && (!mix || random.nextBoolean());

        return switch (currentStreak) {
            case 1 -> new SoundStage(useFemale ? "female_firstblood" : "firstblood", "FIRST BLOOD!");
            case 2 -> new SoundStage(useFemale ? "female_doublekill" : "doublekill", "DOUBLE KILL!");
            case 3 -> new SoundStage(useFemale ? "female_triplekill" : "triplekill", "TRIPLE KILL!");
            case 4 -> new SoundStage(useFemale ? "female_megakill" : "megakill", "MEGA KILL!");
            case 5 -> new SoundStage(useFemale ? "female_ultrakill" : "ultrakill", "ULTRA KILL!");
            case 6 -> new SoundStage(useFemale ? "female_rampage" : "rampage", "RAMPAGE!");
            case 7 -> new SoundStage(useFemale ? "female_killingspree" : "killingspree", "KILLING SPREE!");
            case 8 -> new SoundStage(useFemale ? "female_dominating" : "dominating", "DOMINATING!");
            case 9 -> new SoundStage(useFemale ? "female_unstoppable" : "unstoppable", "UNSTOPPABLE!");
            case 10 -> new SoundStage(useFemale ? "female_wickedsick" : "whickedsick", "WICKED SICK!");
            case 11 -> new SoundStage(useFemale ? "female_monsterkill" : "monsterkill", "MONSTER KILL!");
            case 12 -> new SoundStage(useFemale ? "female_godlike" : "godlike", "GODLIKE!");
            case 13 -> new SoundStage(useFemale ? "female_holyshit" : "holyshit", "HOLY SHIT!");
            case 14 -> new SoundStage(useFemale ? "female_ownage" : "ownage", "OWNAGE!");
            case 15 -> new SoundStage(useFemale ? "female_victory" : "flawlessvictory", "YOU ARE VICTORIOUS!");
            default -> {
                if (useFemale) {
                    String[] femaleGodTier = {
                            "female_rampage",
                            "female_monsterkill",
                            "female_godlike",
                            "female_holyshit",
                            "female_wickedsick",
                            "female_unstoppable",
                            "female_ownage"
                    };
                    String[] femaleGodTitles = {
                            "RAMPAGE!",
                            "MONSTER KILL!",
                            "GODLIKE!",
                            "HOLY SHIT!",
                            "WICKED SICK!",
                            "UNSTOPPABLE!",
                            "OWNAGE!"
                    };
                    int idx = (currentStreak - 16) % femaleGodTier.length;
                    if (idx < 0) idx += femaleGodTier.length;
                    yield new SoundStage(femaleGodTier[idx], femaleGodTitles[idx]);
                } else {
                    String[] maleGodTier = {"rampage", "monsterkill", "godlike", "holyshit", "whickedsick", "ludicrouskill", "killingmachine", "massacre"};
                    int idx = (currentStreak - 16) % maleGodTier.length;
                    if (idx < 0) idx += maleGodTier.length;
                    yield new SoundStage(maleGodTier[idx], maleGodTier[idx].toUpperCase() + "!");
                }
            }
        };
    }

    private void playDirectSound(String soundFile, String title) {
        SoundUtil.playSound_wav("killsound/" + soundFile, volume.getValue());

        if (hudNotification.getValue()) {
            NotificationManager.send(title + " (Тест)", NotificationManager.Type.INFO, 2000);
        }
    }

    private void playTestNext() {
        testStreak++;
        if (testStreak > 15) testStreak = 1;
        playSoundAndNotify(testStreak, "Тестовая цель #" + testStreak);
    }

    private void resetState() {
        streak = 0;
        lastKillTime = 0L;
        lastKilledId = -1;
        recentTargets.clear();
    }
}
