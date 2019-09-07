package me.mattstudios.mf.base;

import me.mattstudios.mf.annotations.Alias;
import me.mattstudios.mf.annotations.Completion;
import me.mattstudios.mf.annotations.Default;
import me.mattstudios.mf.annotations.MaxArgs;
import me.mattstudios.mf.annotations.MinArgs;
import me.mattstudios.mf.annotations.Permission;
import me.mattstudios.mf.annotations.SubCommand;
import me.mattstudios.mf.components.CommandData;
import me.mattstudios.mf.components.CompletionHandler;
import me.mattstudios.mf.components.Message;
import me.mattstudios.mf.components.MessageHandler;
import me.mattstudios.mf.components.ParameterHandler;
import me.mattstudios.mf.exceptions.InvalidCompletionIdException;
import me.mattstudios.mf.exceptions.InvalidParamAnnotationException;
import me.mattstudios.mf.exceptions.InvalidParamException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CommandHandler extends Command {

    private Map<String, CommandData> subCommands;

    private ParameterHandler parameterHandler;
    private CompletionHandler completionHandler;
    private MessageHandler messageHandler;

    CommandHandler(ParameterHandler parameterHandler, CompletionHandler completionHandler, MessageHandler messageHandler, CommandBase command, String commandName, List<String> aliases) {
        super(commandName);
        this.parameterHandler = parameterHandler;
        this.completionHandler = completionHandler;
        this.messageHandler = messageHandler;
        setAliases(aliases);

        subCommands = new HashMap<>();

        addSubCommands(command);
    }

    void addSubCommands(CommandBase command) {
        // Iterates through all the methods in the class.
        for (Method method : command.getClass().getDeclaredMethods()) {
            // Checks if the method is public and if it is annotated by @Default or @SubCommand.
            if ((!method.isAnnotationPresent(Default.class) && !method.isAnnotationPresent(SubCommand.class)) || !Modifier.isPublic(method.getModifiers()))
                continue;

            // Checks if default method has no parameters.
            if (method.getParameterCount() == 0)
                throw new InvalidParamException("Method " + method.getName() + " in class " + command.getClass().getName() + " - needs to have Parameters!");

            // Checks if the fist parameter is either a player or a sender.
            if (!method.getParameterTypes()[0].getTypeName().equals(CommandSender.class.getTypeName()) && !method.getParameterTypes()[0].getTypeName().equals(Player.class.getTypeName()))
                throw new InvalidParamException("Method " + method.getName() + " in class " + command.getClass().getName() + " - first parameter needs to be a CommandSender or a Player!");

            // Starts the command data object.
            CommandData commandData = new CommandData(command);
            commandData.setMethod(method);
            // Sets the first parameter as either player or command sender.
            commandData.setFirstParam(method.getParameterTypes()[0]);

            // Checks if it is a default method.
            if (method.isAnnotationPresent(Default.class)) {
                commandData.setDef(true);
                // Checks if there is more than one parameters in the default method.
                if (commandData.getParams().size() != 0)
                    throw new InvalidParamException("Method " + method.getName() + " in class " + command.getClass().getName() + " - Default method cannot have more than one parameter!");
            }

            // Checks if the parameters in class are registered.
            for (int i = 1; i < method.getParameterTypes().length; i++) {
                Class clss = method.getParameterTypes()[i];

                if (clss.equals(String[].class) && i != method.getParameterTypes().length - 1) {
                    throw new InvalidParamException("Method " + method.getName() + " in class " + command.getClass().getName() + " 'String[] args' have to be the last parameter if wants to be used!");
                }

                if (!clss.isEnum() && !this.parameterHandler.isRegisteredType(clss)) {
                    throw new InvalidParamException("Method " + method.getName() + " in class " + command.getClass().getName() + " contains unregistered parameter types!");
                }

                commandData.getParams().add(clss);
            }

            // Checks if permission annotation is present.
            if (method.isAnnotationPresent(Permission.class)) {
                // Checks whether the command sender has the permission set in the annotation.
                commandData.setPermission(method.getAnnotation(Permission.class).value());
            }

            // Checks for completion on the parameters.
            for (int i = 0; i < method.getParameters().length; i++) {
                Parameter parameter = method.getParameters()[i];

                if (i == 0 && parameter.isAnnotationPresent(Completion.class))
                    throw new InvalidParamAnnotationException("Method " + method.getName() + " in class " + command.getClass().getName() + " - First parameter of a command method cannot have Completion annotation!");

                // Checks for max and min args on the String[] parameter
                if (parameter.getType().getTypeName().equals(String[].class.getTypeName())) {
                    if (parameter.isAnnotationPresent(MaxArgs.class)) {
                        commandData.setMaxArgs(parameter.getAnnotation(MaxArgs.class).value());
                    }

                    if (parameter.isAnnotationPresent(MinArgs.class)) {
                        commandData.setMinArgs(parameter.getAnnotation(MinArgs.class).value());
                    }
                }

                if (!parameter.isAnnotationPresent(Completion.class)) continue;

                String[] values = parameter.getAnnotation(Completion.class).value();

                if (values.length != 1)
                    throw new InvalidParamAnnotationException("Method " + method.getName() + " in class " + command.getClass().getName() + " - Parameter completion can only have one value!");
                if (!values[0].startsWith("#"))
                    throw new InvalidCompletionIdException("Method " + method.getName() + " in class " + command.getClass().getName() + " - The completion ID must start with #!");

                if (!this.completionHandler.isRegistered(values[0]))
                    throw new InvalidCompletionIdException("Method " + method.getName() + " in class " + command.getClass().getName() + " - Unregistered completion ID '" + values[0] + "'!");

                commandData.getCompletions().put(i, values[0]);
            }

            // Checks for completion annotation in the method.
            if (method.isAnnotationPresent(Completion.class)) {
                String[] completionValues = method.getAnnotation(Completion.class).value();
                for (int i = 0; i < completionValues.length; i++) {
                    String id = completionValues[i];

                    if (!id.startsWith("#"))
                        throw new InvalidCompletionIdException("Method " + method.getName() + " in class " + command.getClass().getName() + " - The completion ID must start with #!");

                    if (!this.completionHandler.isRegistered(id))
                        throw new InvalidCompletionIdException("Method " + method.getName() + " in class " + command.getClass().getName() + " - Unregistered completion ID'" + id + "'!");

                    commandData.getCompletions().put(i + 1, id);
                }
            }

            // Checks for aliases.
            if (method.isAnnotationPresent(Alias.class)) {
                // Iterates through the alias and add each as a normal sub command.
                for (String alias : method.getAnnotation(Alias.class).value()) {
                    //noinspection UnnecessaryLocalVariable
                    CommandData aliasCD = commandData;
                    if (aliasCD.isDef()) aliasCD.setDef(false);
                    subCommands.put(alias, aliasCD);
                }
            }

            // puts the main method in the list.
            if (!commandData.isDef() || method.isAnnotationPresent(SubCommand.class))
                subCommands.put(method.getAnnotation(SubCommand.class).value(), commandData);
        }
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] arguments) {
        // Runs default command here as arguments are 0 or empty.
        if (arguments.length == 0 || arguments[0].isEmpty()) {

            CommandData commandData = getDefaultMethod();

            // Will not run if there is no default methods.
            if (commandData == null) return true;

            // Checks if permission annotation is present.
            if (commandData.hasPermission()) {
                // Checks whether the command sender has the permission set in the annotation.
                if (!sender.hasPermission(commandData.getPermission())) {
                    messageHandler.sendMessage(Message.NO_PERMISSION, sender, null);
                    return true;
                }
            }

            // Checks if the command can be accessed from console
            if (!commandData.getFirstParam().getTypeName().equals(CommandSender.class.getTypeName()) && !(sender instanceof Player)) {
                messageHandler.sendMessage(Message.NO_CONSOLE, sender, null);
                return true;
            }

            // Executes all the commands.
            return executeCommand(commandData, sender, arguments, true);
        }

        // Checks if the sub command is registered or not.
        if (!subCommands.containsKey(arguments[0])) {
            messageHandler.sendMessage(Message.DOESNT_EXISTS, sender, arguments[0]);
            return true;
        }

        // Gets the method from the list.
        CommandData commandData = subCommands.get(arguments[0]);

        // Checks if permission annotation is present.
        // Checks whether the command sender has the permission set in the annotation.
        if (commandData.hasPermission() && !sender.hasPermission(commandData.getPermission())) {
            messageHandler.sendMessage(Message.NO_PERMISSION, sender, null);
            return true;
        }

        // Checks if the command can be accessed from console
        if (!commandData.getFirstParam().getTypeName().equals(CommandSender.class.getTypeName()) && !(sender instanceof Player)) {
            messageHandler.sendMessage(Message.NO_CONSOLE, sender, null);
            return true;
        }

        // Runs the command executor.
        return executeCommand(commandData, sender, arguments, false);
    }

    private boolean executeCommand(CommandData commandData, CommandSender sender, String[] arguments, boolean def) {
        try {

            Method method = commandData.getMethod();

            // Checks if it the command is default and remove the sub command argument one if it is not.
            List<String> argumentsList = new LinkedList<>(Arrays.asList(arguments));
            if (!def && argumentsList.size() > 0) argumentsList.remove(0);

            // Check if the method only has a sender as parameter.
            if (commandData.getParams().size() == 0 && argumentsList.size() == 0) {
                method.invoke(commandData.getCommand(), sender);
                return true;
            }

            // Checks if it is a default type command with just sender and args.
            if (commandData.getParams().size() == 1
                    && commandData.getParams().get(0).getTypeName().equals(String[].class.getTypeName())) {
                method.invoke(commandData.getCommand(), sender, arguments);
                return true;
            }

            // Checks for correct command usage.
            if (commandData.getParams().size() != argumentsList.size()
                    && !commandData.getParams().get(commandData.getParams().size() - 1).getTypeName().equals(String[].class.getTypeName())) {
                messageHandler.sendMessage(Message.WRONG_USAGE, sender, null);
                return true;
            }

            // Creates a list of the params to send.
            List<Object> invokeParams = new ArrayList<>();
            // Adds the sender as one of the params.
            invokeParams.add(sender);

            // Iterates through all the parameters to check them.
            for (int i = 0; i < commandData.getParams().size(); i++) {
                Class parameter = commandData.getParams().get(i);

                if (commandData.getParams().size() > argumentsList.size()) {
                    messageHandler.sendMessage(Message.WRONG_USAGE, sender, null);
                    return true;
                }

                Object argument = argumentsList.get(i);

                // Checks for String[] args.
                if (parameter.equals(String[].class)) {
                    String[] args = new String[argumentsList.size() - i];

                    if (commandData.getMaxArgs() != 0 && args.length > commandData.getMaxArgs()) {
                        messageHandler.sendMessage(Message.WRONG_USAGE, sender, null);
                        return true;
                    }

                    if (commandData.getMinArgs() != 0 && args.length < commandData.getMinArgs()) {
                        messageHandler.sendMessage(Message.WRONG_USAGE, sender, null);
                        return true;
                    }


                    for (int j = 0; j < args.length; j++) {
                        args[j] = argumentsList.get(i + j);
                    }

                    argument = args;
                }

                Object result;
                // Checks weather the parameter is an enum, because it needs to be sent as Enum.class.
                if (parameter.isEnum())
                    result = parameterHandler.getTypeResult(Enum.class, argument, sender, parameter);
                else result = parameterHandler.getTypeResult(parameter, argument, sender);

                // Will be null if error occurs.
                if (result == null) return true;
                invokeParams.add(result);
            }

            method.invoke(commandData.getCommand(), invokeParams.toArray());

            return true;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {

        // Checks if args is 1 so it sends the sub comments completion.
        if (args.length == 1) {
            List<String> commandNames = new ArrayList<>();

            // Checks if the typing command is empty.
            if (!args[0].equals("")) {
                for (String commandName : subCommands.keySet()) {
                    if (!commandName.startsWith(args[0].toLowerCase())) continue;
                    commandNames.add(commandName);
                }
            } else {
                commandNames = new ArrayList<>(subCommands.keySet());
            }

            // Sorts the sub commands by alphabetical order.
            Collections.sort(commandNames);

            // The complete values.
            return commandNames;
        }

        String subCommand = args[0];

        // Checks if it contains the sub command; Should always be true.
        if (!subCommands.containsKey(subCommand)) return super.tabComplete(sender, alias, args);

        CommandData commandData = subCommands.get(subCommand);

        // Checks if the completion list has the current args position.
        if (!commandData.getCompletions().containsKey(args.length - 1)) return super.tabComplete(sender, alias, args);

        // Gets the current ID.
        String id = commandData.getCompletions().get(args.length - 1);

        // Checks one more time if the ID is registered.
        if (!completionHandler.isRegistered(id)) return super.tabComplete(sender, alias, args);

        List<String> completionList = new ArrayList<>();
        Object inputClss = commandData.getParams().get(args.length - 2);

        // TODO range without thingy and also for double
        if (id.contains(":")) {
            String[] values = id.split(":");
            id = values[0];
            inputClss = values[1];
        }

        String current = args[args.length - 1];

        // Checks if the typing completion is empty.
        if (!"".equals(current)) {
            for (String completion : completionHandler.getTypeResult(id, inputClss)) {
                if (!completion.toLowerCase().contains(current.toLowerCase())) continue;
                completionList.add(completion);
            }
        } else {
            System.out.println(inputClss.toString());
            System.out.println(id);
            completionList = new ArrayList<>(completionHandler.getTypeResult(id, inputClss));
        }

        // Sorts the completion content by alphabetical order.
        Collections.sort(completionList);

        // The complete values.
        return completionList;
    }

    /**
     * Gets the default method from the Command Data objects.
     *
     * @return The Command data of the default method if there is one.
     */
    private CommandData getDefaultMethod() {
        for (String subCommand : subCommands.keySet()) {
            if (subCommands.get(subCommand).isDef()) return subCommands.get(subCommand);
        }
        return null;
    }
}
