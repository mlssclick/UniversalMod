package universalmod.module;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleInformation {
    String moduleName();


    ModuleCategory moduleCategory();

    int moduleKeybind() default -1;
}
