package me.caitlyn.opensockets;

import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.internal.Microcontroller;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class SocketCardDriver extends DriverItem implements HostAware {
    public SocketCardDriver(Item item) {
        super(new ItemStack(item));
    }

    @Override
    public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
        return isComputer(host) || Microcontroller.class.isAssignableFrom(host);
    }

    @Override
    public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        return new SocketEnvironment(host);
    }

    @Override
    public String slot(ItemStack stack) {
        return "card";
    }
}
