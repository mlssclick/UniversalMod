package universalmod.command;

import net.minecraft.text.Text;
import universalmod.command.CommandManager;

import java.util.List;
import java.util.function.Function;

public final class Paginator<T> {
    private final List<T> items;
    private final int pageSize;
    private int page = 1;

    public Paginator(List<T> items) {
        this(items, 8);
    }

    public Paginator(List<T> items, int pageSize) {
        this.items = items == null ? List.of() : items;
        this.pageSize = Math.max(1, pageSize);
    }

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public void display(Runnable header, Function<T, Text> rowFactory, String baseCommand) {
        if (header != null) header.run();
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));
        int safe = Math.min(page, pages), start = (safe - 1) * pageSize, end = Math.min(items.size(), start + pageSize);
        CommandManager m = CommandManager.getInstance();
        for (int i = start; i < end; i++) {
            Text row = rowFactory.apply(items.get(i));
            if (m != null) m.sendRaw(row);
        }
        if (m != null && pages > 1) m.sendMessage("Page " + safe + "/" + pages + " | " + baseCommand + " <page>");
    }
}
