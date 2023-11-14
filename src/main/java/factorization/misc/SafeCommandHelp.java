package factorization.misc;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHelp;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SafeCommandHelp extends CommandHelp {

    @Override
    public String getCommandName() {
        return "safe-help";
    }

    @Override
    public String getCommandUsage(ICommandSender player) {
        return "/safe-help -- Crash resistant /help";
    }

    @Override
    public List<String> getCommandAliases() {
        return null;
    }

    @Override
    protected List<ICommand> getSortedPossibleCommands(ICommandSender player) {
        List<ICommand> b = super.getSortedPossibleCommands(player);
        return b.stream().map(SafetyWrap::new).collect(Collectors.toList());
    }

    @Override
    protected Map<String, ICommand> getCommands() {
        Map<String, ICommand> b = super.getCommands();
        return b.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, c -> new SafetyWrap(c.getValue()), (a, b1) -> b1));
    }

    @Override
    public void processCommand(ICommandSender player, String[] args) {
        try {
            super.processCommand(player, args);
        } catch (CommandException t) {
            throw t;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    static class SafetyWrap implements ICommand {
        final ICommand base;

        SafetyWrap(ICommand base) {
            this.base = base;
        }

        @Override
        public String getCommandName() {
            try {
                String name = base.getCommandName();
                if (name == null || name.isEmpty()) {
                    return "<Unnamed command: " + base.getClass() + ">";
                }
                return base.getCommandName();
            } catch (Throwable t) {
                t.printStackTrace();
                return "<Command with erroring name: " + base.getClass() + ">";
            }
        }

        @Override
        public String getCommandUsage(ICommandSender player) {
            try {
                String usage = base.getCommandUsage(player);
                if (usage == null || usage.isEmpty()) {
                    return "<Command with no usage:" + base.getClass() + ">";
                }
                return usage;
            } catch (Throwable t) {
                return "<Command with erroring usage: " + base.getClass() + ">";
            }
        }

        @Override
        public List<String> getCommandAliases() {
            return base.getCommandAliases();
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            // Doesn't happen.
            // Even if it did happen, it isn't reasonable to catch & ignore any exceptiosn it might throw.
            base.processCommand(sender, args);
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender player) {
            try {
                return base.canCommandSenderUseCommand(player);
            } catch (Throwable t) {
                t.printStackTrace();
                return false;
            }
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender player, String[] args) {
            try {
                return base.addTabCompletionOptions(player, args);
            } catch (Throwable t) {
                // Uh. Yeah, not sure.
                t.printStackTrace();
                return null;
            }
        }

        @Override
        public boolean isUsernameIndex(String[] args, int id) {
            try {
                return base.isUsernameIndex(args, id);
            } catch (Throwable t) {
                t.printStackTrace();
                return false;
            }
        }

        @Override
        public int compareTo(@Nonnull Object obj) {
            try {
                return base.getClass().getName().compareTo(obj.getClass().getName());
            } catch (Throwable t) {
                final int a = System.identityHashCode(base);
                final int b = System.identityHashCode(obj);
                return Integer.compare(a, b);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SafetyWrap) {
                obj = ((SafetyWrap) obj).base;
            }
            return base.equals(obj);
        }
    }
}
