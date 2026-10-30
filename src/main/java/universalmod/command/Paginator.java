package universalmod.command;

import net.minecraft.network.chat.Component;
import universalmod.command.CommandManager;

import java.util.List;
import java.util.function.Function;

public final class Paginator<T> {
    private final List<T> items;
    private final int pageSize;
    private final CommandManager manager;
    private int page = 1;

    public Paginator(CommandManager manager, List<T> items) {
        this(manager, items, 8);
    }

    public Paginator(CommandManager manager, List<T> items, int pageSize) {
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
        this.items = items == null ? List.of() : items;
        this.pageSize = Math.max(1, pageSize);
    }

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public void display(Runnable header, Function<T, Component> rowFactory, String baseCommand) {
        if (header != null) {
            header.run();
        }
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));
        int safePage = Math.min(page, pages);
        int start = (safePage - 1) * pageSize;
        int end = Math.min(items.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            Component row = rowFactory.apply(items.get(i));
            manager.sendRaw(row);
        }
        if (pages > 1) {
            manager.sendMessage("Page " + safePage + "/" + pages + " | " + baseCommand + " <page>");
        }
    }
}
