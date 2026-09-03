package fun.newrar.module.impl.utils;

import net.minecraft.item.Item;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.other.Instance;

@ModuleInfo(
        name = "Item Scroller",
        desc = "Быстрое перемещение предметов в открытых контейнерах прокруткой колеса мыши",
        category = Category.OTHER
)
public class ItemScroller extends Module {
    public static ItemScroller getInstance() {
        return Instance.get(ItemScroller.class);
    }

    public SliderSetting delay = new SliderSetting(this,"Задержка",50,0,100,1);
}

