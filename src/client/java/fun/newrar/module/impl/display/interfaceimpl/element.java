package fun.newrar.module.impl.display.interfaceimpl;

import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.utils.annotation.IMinecraft;

public interface element extends IMinecraft {
    void onRender(DragSetting dragSetting, InterFace interFace);
}
