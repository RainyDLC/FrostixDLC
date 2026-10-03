package rtx.kimiko.api.chat.commands.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import rtx.kimiko.api.chat.commands.Command;
import rtx.kimiko.api.chat.commands.helpers.CommandDividers;
import rtx.kimiko.api.chat.commands.helpers.TabCompleteHelper;
import rtx.kimiko.utils.chat.ChatMessage;
import rtx.kimiko.utils.storage.friend.FriendUtils;

public final class FriendCommand extends Command {

    public FriendCommand() {
        super("friend", "Manage your friends list", new String[]{"friends", "f"});
    }

    @Override
    public void execute(@NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            this.friendUsage();
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add": {
                this.addFriend(args);
                break;
            }
            case "del":
            case "delete":
            case "remove": {
                this.removeFriend(args);
                break;
            }
            case "list": {
                this.listFriends();
                break;
            }
            case "clear": {
                FriendUtils.clearAndSave();
                this.logDirect("Friends list cleared.");
                break;
            }
            default: {
                this.usage();
            }
        }
    }

    private void addFriend(String[] args) {
        if (args.length < 2) {
            this.logDirect("Usage: friend add <ник>", Formatting.RED);
            return;
        }
        String name = this.joinArgs(args, 1);
        if (FriendUtils.isFriend(name)) {
            this.logDirect(name + " is already your friend.", Formatting.RED);
            return;
        }
        FriendUtils.addFriendAndSave(name);
        this.logDirect(ChatMessage.accentGradient(name + " added to friends."));
    }

    private void removeFriend(String[] args) {
        if (args.length < 2) {
            this.logDirect("Usage: friend remove <ник>", Formatting.RED);
            return;
        }
        String name = this.joinArgs(args, 1);
        if (!FriendUtils.isFriend(name)) {
            this.logDirect(name + " is not your friend.", Formatting.RED);
            return;
        }
        FriendUtils.removeFriendAndSave(name);
        this.logDirect(ChatMessage.accentGradient(name + " removed from friends."));
    }

    private void listFriends() {
        List<String> friends = FriendUtils.getFriendNames();
        if (friends.isEmpty()) {
            this.logDirect("Your friends list is empty.", Formatting.RED);
            return;
        }
        String title = "FRIENDS";
        List<String> rowTexts = new ArrayList<>();
        for (String friend : friends) {
            rowTexts.add(friend);
        }
        int lineCount = CommandDividers.calcLineCountForContent(title, rowTexts);
        this.logDirectRaw(CommandDividers.header(title, lineCount));
        for (String friend : friends) {
            this.logDirect(" • " + friend);
        }
        this.logDirectRaw(CommandDividers.footer(title, lineCount));
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    @NotNull
    public Stream<String> tabComplete(@NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String[] actions = new String[]{"add", "remove", "list", "clear"};
            return new TabCompleteHelper().append(actions).sortAlphabetically().filterPrefix(args[0]).stream();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("delete"))) {
            Collection<String> friends = FriendUtils.getFriendNames();
            String[] names = friends.toArray(new String[0]);
            return new TabCompleteHelper().append(Arrays.copyOf(names, names.length)).filterPrefix(args[1]).stream();
        }
        return Stream.empty();
    }

    private void friendUsage() {
        this.logDirect("Usage:", Formatting.RED);
        this.logDirect("  .friend add <ник>");
        this.logDirect("  .friend remove <ник>");
        this.logDirect("  .friend list");
        this.logDirect("  .friend clear");
    }
}
