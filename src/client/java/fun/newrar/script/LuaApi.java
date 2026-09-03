package fun.newrar.script;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import fun.newrar.Client;
import fun.newrar.friend.FriendManager;
import fun.newrar.module.api.Module;
import fun.newrar.utils.math.ChatUtils;

public final class LuaApi {
    private LuaApi() {
    }

    static LuaTable build() {
        LuaTable api = new LuaTable();

        api.set("chat", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue msg) {
                ChatUtils.addChatMessage(msg.tojstring());
                return LuaValue.NIL;
            }
        });

        api.set("player_name", safe(() -> mc().player != null ? mc().player.getName().getString() : ""));
        api.set("player_x", safeNum(() -> mc().player != null ? mc().player.getX() : 0));
        api.set("player_y", safeNum(() -> mc().player != null ? mc().player.getY() : 0));
        api.set("player_z", safeNum(() -> mc().player != null ? mc().player.getZ() : 0));
        api.set("player_yaw", safeNum(() -> mc().player != null ? mc().player.getYaw() : 0));
        api.set("player_pitch", safeNum(() -> mc().player != null ? mc().player.getPitch() : 0));
        api.set("player_health", safeNum(() -> mc().player != null ? mc().player.getHealth() : 0));
        api.set("player_food", safeNum(() -> mc().player != null ? mc().player.getHungerManager().getFoodLevel() : 0));
        api.set("player_fall_distance", safeNum(() -> mc().player != null ? mc().player.fallDistance : 0));
        api.set("player_on_ground", safe(() -> mc().player != null && mc().player.isOnGround()));

        api.set("world_time", safeNum(() -> mc().world != null ? mc().world.getTime() : 0));
        api.set("world_raining", safe(() -> mc().world != null && mc().world.isRaining()));
        api.set("world_thundering", safe(() -> mc().world != null && mc().world.isThundering()));

        api.set("key_down", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue code) {
                try {
                    return LuaValue.valueOf(InputUtil.isKeyPressed(mc().getWindow(), code.toint()));
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        });
        api.set("time_ms", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(System.currentTimeMillis());
            }
        });

        api.set("module_enabled", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue name) {
                Module m = Client.get() != null && Client.get().moduleManager() != null
                        ? Client.get().moduleManager().getModule(name.tojstring()) : null;
                return LuaValue.valueOf(m != null && m.isEnabled());
            }
        });
        api.set("toggle_module", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue name, LuaValue state) {
                Module m = Client.get() != null && Client.get().moduleManager() != null
                        ? Client.get().moduleManager().getModule(name.tojstring()) : null;
                if (m == null) return LuaValue.FALSE;
                boolean target = state.isnil() ? !m.isEnabled() : state.toboolean();
                m.setEnabled(target, true);
                return LuaValue.valueOf(target);
            }
        });

        api.set("is_friend", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue name) {
                FriendManager fm = Client.get() != null ? Client.get().friendManager() : null;
                return LuaValue.valueOf(fm != null && fm.isFriend(name.tojstring()));
            }
        });

        return api;
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private static ZeroArgFunction safe(java.util.function.Supplier<Object> supplier) {
        return new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                try {
                    Object v = supplier.get();
                    if (v instanceof Boolean b) return LuaValue.valueOf(b);
                    if (v instanceof Number n) return LuaValue.valueOf(n.doubleValue());
                    return LuaValue.valueOf(String.valueOf(v));
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        };
    }

    private static ZeroArgFunction safeNum(java.util.function.DoubleSupplier supplier) {
        return new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                try {
                    return LuaValue.valueOf(supplier.getAsDouble());
                } catch (Exception e) {
                    return LuaValue.ZERO;
                }
            }
        };
    }
}

