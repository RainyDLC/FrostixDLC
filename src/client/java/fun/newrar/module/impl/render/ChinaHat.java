package fun.newrar.module.impl.render;

import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.utils.other.Instance;

@ModuleInfo(
        name = "China Hat",
        desc = "Конусообразный головной убор с градиентной подсветкой над моделью персонажа",
        category = Category.RENDER
)
public class ChinaHat extends Module {
    public static ChinaHat getInstance() {
        return Instance.get(ChinaHat.class);
    }
}

