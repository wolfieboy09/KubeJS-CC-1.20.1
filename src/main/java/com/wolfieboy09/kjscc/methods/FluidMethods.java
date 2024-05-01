package com.wolfieboy09.kjscc.methods;

import com.wolfieboy09.kjscc.KJSCC;
import dan200.computercraft.api.detail.ForgeDetailRegistries;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class FluidMethods implements GenericPeripheral {
    @Nonnull
    @Override
    public PeripheralType getType()
    {
        return PeripheralType.ofAdditional( "fluid_storage" );
    }

    @Nonnull
    @Override
    public ResourceLocation id()
    {
        return new ResourceLocation( KJSCC.MOD_ID, "fluid" );
    }


    @LuaFunction( mainThread = true )
    public static Map<Integer, Map<String, ?>> tanks( IFluidHandler fluids )
    {
        Map<Integer, Map<String, ?>> result = new HashMap<>();
        int size = fluids.getTanks();
        for( int i = 0; i < size; i++ )
        {
            FluidStack stack = fluids.getFluidInTank( i );
            if( !stack.isEmpty() ) result.put( i + 1, ForgeDetailRegistries.FLUID_STACK.getBasicDetails( stack ) );
        }

        return result;
    }

    @LuaFunction( mainThread = true )
    public static int pushFluid(
            IFluidHandler from, IComputerAccess computer,
            String toName, Optional<Integer> limit, Optional<String> fluidName
    ) throws LuaException
    {
        Fluid fluid = fluidName.isPresent()
                ? getRegistryEntry( fluidName.get(), "fluid", ForgeRegistries.FLUIDS )
                : null;

        // Find location to transfer to
        IPeripheral location = computer.getAvailablePeripheral( toName );
        if( location == null ) throw new LuaException( "Target '" + toName + "' does not exist" );

        IFluidHandler to = extractHandler( location.getTarget() );
        if( to == null ) throw new LuaException( "Target '" + toName + "' is not an tank" );

        int actualLimit = limit.orElse( Integer.MAX_VALUE );
        if( actualLimit <= 0 ) throw new LuaException( "Limit must be > 0" );

        return fluid == null
                ? moveFluid( from, actualLimit, to )
                : moveFluid( from, new FluidStack( fluid, actualLimit ), to );
    }

    /**
     * Move a fluid from a connected fluid container into this oneone.
     * <p>
     * This allows you to pull fluid in the current fluid container from another container <em>on the same wired
     * network</em>. Both containers must attached to wired modems which are connected via a cable.
     *
     * @param to        Container to move fluid to.
     * @param computer  The current computer.
     * @param fromName  The name of the peripheral/container to push to. This is the string given to @{peripheral.wrap},
     *                  and displayed by the wired modem.
     * @param limit     The maximum amount of fluid to move.
     * @param fluidName The fluid to move. If not given, an arbitrary fluid will be chosen.
     * @return The amount of moved fluid.
     * @throws LuaException If the peripheral to transfer to doesn't exist or isn't an fluid container.
     * @cc.see peripheral.getName Allows you to get the name of a @{peripheral.wrap|wrapped} peripheral.
     */
    @LuaFunction( mainThread = true )
    public static int pullFluid(
            IFluidHandler to, IComputerAccess computer,
            String fromName, Optional<Integer> limit, Optional<String> fluidName
    ) throws LuaException
    {
        Fluid fluid = fluidName.isPresent()
                ? getRegistryEntry( fluidName.get(), "fluid", ForgeRegistries.FLUIDS )
                : null;

        // Find location to transfer to
        IPeripheral location = computer.getAvailablePeripheral( fromName );
        if( location == null ) throw new LuaException( "Target '" + fromName + "' does not exist" );

        IFluidHandler from = extractHandler( location.getTarget() );
        if( from == null ) throw new LuaException( "Target '" + fromName + "' is not an tank" );

        int actualLimit = limit.orElse( Integer.MAX_VALUE );
        if( actualLimit <= 0 ) throw new LuaException( "Limit must be > 0" );

        return fluid == null
                ? moveFluid( from, actualLimit, to )
                : moveFluid( from, new FluidStack( fluid, actualLimit ), to );
    }

    @Nullable
    private static IFluidHandler extractHandler( @Nullable Object object )
    {
        if( object instanceof BlockEntity blockEntity && blockEntity.isRemoved() ) return null;

        if( object instanceof ICapabilityProvider provider )
        {
            LazyOptional<IFluidHandler> cap = provider.getCapability( ForgeCapabilities.FLUID_HANDLER );
            if( cap.isPresent() ) return cap.orElseThrow( NullPointerException::new );
        }

        if( object instanceof IFluidHandler handler ) return handler;
        return null;
    }

    /**
     * Move fluid from one handler to another.
     *
     * @param from  The handler to move from.
     * @param limit The maximum amount of fluid to move.
     * @param to    The handler to move to.
     * @return The amount of fluid moved.
     */
    private static int moveFluid( IFluidHandler from, int limit, IFluidHandler to )
    {
        return moveFluid( from, from.drain( limit, IFluidHandler.FluidAction.SIMULATE ), limit, to );
    }

    /**
     * Move fluid from one handler to another.
     *
     * @param from  The handler to move from.
     * @param fluid The fluid and limit to move.
     * @param to    The handler to move to.
     * @return The amount of fluid moved.
     */
    private static int moveFluid( IFluidHandler from, FluidStack fluid, IFluidHandler to )
    {
        return moveFluid( from, from.drain( fluid, IFluidHandler.FluidAction.SIMULATE ), fluid.getAmount(), to );
    }

    /**
     * Move fluid from one handler to another.
     *
     * @param from      The handler to move from.
     * @param extracted The fluid which is extracted from {@code from}.
     * @param limit     The maximum amount of fluid to move.
     * @param to        The handler to move to.
     * @return The amount of fluid moved.
     */
    private static int moveFluid( IFluidHandler from, FluidStack extracted, int limit, IFluidHandler to )
    {
        if( extracted == null || extracted.getAmount() <= 0 ) return 0;

        // Limit the amount to extract.
        extracted = extracted.copy();
        extracted.setAmount( Math.min( extracted.getAmount(), limit ) );

        int inserted = to.fill( extracted.copy(), IFluidHandler.FluidAction.EXECUTE );
        if( inserted <= 0 ) return 0;

        // Remove the item from the original inventory. Technically this could fail, but there's little we can do
        // about that.
        extracted.setAmount( inserted );
        from.drain( extracted, IFluidHandler.FluidAction.EXECUTE );
        return inserted;
    }
}