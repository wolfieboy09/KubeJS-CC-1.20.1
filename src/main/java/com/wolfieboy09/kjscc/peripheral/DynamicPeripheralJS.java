package com.wolfieboy09.kjscc.peripheral;

import com.wolfieboy09.kjscc.result.IResultJS;
import com.wolfieboy09.kjscc.result.MultiResultJS;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.latvian.mods.kubejs.level.BlockContainerJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DynamicPeripheralJS implements IDynamicPeripheral {
    protected final String[] names;
    protected final PeripheralMethod[] methods;
    protected final BlockContainerJS block;
    protected final Direction side;
    protected final String type;

    @FunctionalInterface
    public interface PeripheralCallback {
        Object call(BlockContainerJS block, Direction side, List arguments, IComputerAccess computer, ILuaContext context);
    }

    public DynamicPeripheralJS(String type, Level world, BlockPos pos, Direction side,@NotNull List<PeripheralMethod> nameMethods) {
        this.block = new BlockContainerJS(world, pos);
        this.side = side;
        this.type = type;
        List<String> names = new ArrayList<>();
        nameMethods.forEach(pair -> names.add(pair.type()));
        this.names = names.toArray(String[]::new);
        this.methods = nameMethods.toArray(PeripheralMethod[]::new);
    }

    @NotNull
    public String[] getMethodNames() {
        return names;
    }

    static private List argsAsList (IArguments args) throws LuaException {
        List l = new ArrayList();
        for (int i = 0; i < args.count(); i++) {
            l.add(args.get(i));
        }
        return l;
    }
    @NotNull
    @Override
    public final MethodResult callMethod(@NotNull IComputerAccess computer, @NotNull ILuaContext context, int method, @NotNull IArguments arguments) throws LuaException {
        try {
            PeripheralMethod peripheralMethod = methods[method];
            List argsList = argsAsList(arguments);
            if (peripheralMethod.mainThread()) {
                return context.executeMainThreadTask(() -> {
                    IResultJS result = IResultJS.getLuaType(peripheralMethod.callback().call(block, side, argsList, computer, context));
                    return result instanceof MultiResultJS ? (Object[]) result.getConvertedResult() : new Object[]{result.getConvertedResult()};
                });
            } else {
                IResultJS result = IResultJS.getLuaType(peripheralMethod.callback().call(block, side, argsList, computer, context));
                return MethodResult.of(result instanceof MultiResultJS ? (Object[]) result.getConvertedResult() : new Object[]{result.getConvertedResult()});
            }
        } catch (Exception e) {
            throw new LuaException(e.getMessage());
        }
    }

    @NotNull
    public String getType() {
        return type;
    }

    public Object getTarget () {
        BlockEntity ent = block.getEntity();
        if (ent != null) {
            return ent;
        } else {
            return block;
        }
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof DynamicPeripheralJS peripheralJS &&
                peripheralJS.type.equals(type) &&
                peripheralJS.block.equals(block);
    }
}