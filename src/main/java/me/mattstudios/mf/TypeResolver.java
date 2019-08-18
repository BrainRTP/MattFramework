package me.mattstudios.mf;

import org.bukkit.entity.Player;

@FunctionalInterface
interface TypeResolver {

    /**
     * Resolves the type of class and returns the function registered.
     * @param object The object to be tested.
     * @return The result of the function.
     */
    Object getResolved(Object object, Player player);

}
