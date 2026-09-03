package fun.newrar.module.impl.utils;

import fun.newrar.Client;
import fun.newrar.manager.event_impl.TextFactoryEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;

@ModuleInfo(
        name = "Name Protect",
        category = Category.OTHER,
        desc = "Подмена собственного никнейма и имен друзей для обеспечения конфиденциальности на записи"
)
public class NameProtect extends Module {
    public BooleanSetting friends = new BooleanSetting(this,"Скрывать друзей",true);
    public BooleanSetting anarhy = new BooleanSetting(this,"Анархию",false);

    @EventHandler
    public void onEvent(TextFactoryEvent e) {
        if(anarhy.getValue())
        e.replaceRegex("(?ui)Анархия-(?:(?:1\\d{3})|(?:[1-9]\\d{0,2})|2000)", "RainyProject");

        e.replaceText(mc.getSession().getUsername(), "rainydlc");

        e.replaceRegex("funtime", "Успешный проект");

        e.replaceRegex("FunTime.su", "Успешный проект");
        e.replaceRegex("Анархия", "HvH");

        if (friends.getValue()) {
            replaceFriendNames(e);
        }
    }

    private void replaceFriendNames(TextFactoryEvent e) {
        for (String friend : Client.get().friendManager().getFriends()) {
            e.replaceText(friend, "Friend");
        }
    }
}

