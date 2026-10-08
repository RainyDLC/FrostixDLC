package rainy.fun.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModuleManager {
    private final Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();

    public <T extends Module> T register(T module) {
        Module previous = modules.putIfAbsent(module.getClass(), module);
        if (previous != null) {
            throw new IllegalArgumentException("Module already registered: " + module.getClass().getName());
        }
        return module;
    }

    public <T extends Module> Optional<T> get(Class<T> type) {
        return modules.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    public Optional<Module> getByName(String name) {
        return modules.values().stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public List<Module> getByCategory(ModuleCategory category) {
        return modules.values().stream()
                .filter(module -> module.getCategory() == category)
                .toList();
    }

    public Collection<Module> getAll() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public void tickEnabled() {
        for (Module module : List.copyOf(modules.values())) {
            module.tickIfEnabled();
        }
    }

    public Map<ModuleCategory, List<Module>> snapshotByCategory() {
        Map<ModuleCategory, List<Module>> snapshot = new EnumMap<>(ModuleCategory.class);
        for (ModuleCategory category : ModuleCategory.values()) {
            snapshot.put(category, new ArrayList<>(getByCategory(category)));
        }
        return Collections.unmodifiableMap(snapshot);
    }
}
