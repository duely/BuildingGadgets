package com.direwolf20.buildinggadgets.Items;

import com.direwolf20.buildinggadgets.BuildingGadgets;
import com.direwolf20.buildinggadgets.Config;
import com.direwolf20.buildinggadgets.Entities.BlockBuildEntity;
import com.direwolf20.buildinggadgets.ModBlocks;
import com.direwolf20.buildinggadgets.Tools.ExchangingModes;
import com.direwolf20.buildinggadgets.Tools.InventoryManipulation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.*;

import static net.minecraft.block.BlockStainedGlass.COLOR;

public class ExchangerTool extends Item {

    private static final BlockRenderLayer[] LAYERS = BlockRenderLayer.values();
    private static final FakeBuilderWorld fakeWorld = new FakeBuilderWorld();
    float rayTraceRange = 20f; //Range of the tool's working mode @todo make this a config

    public enum toolModes {
        Wall,VerticalColumn,HorizontalColumn;
        private static ExchangerTool.toolModes[] vals = values();
        public ExchangerTool.toolModes next()
        {
            return vals[(this.ordinal()+1) % vals.length];
        }
    }

    public ExchangerTool() {
        setRegistryName("exchangertool");        // The unique name (within your mod) that identifies this item
        setUnlocalizedName(BuildingGadgets.MODID + ".exchangertool");     // Used for localization (en_US.lang)
        setMaxStackSize(1);
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(getRegistryName(), "inventory"));
    }

    public NBTTagCompound initToolTag (ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = new NBTTagCompound();
            stack.setTagCompound(tagCompound);
            tagCompound.setString("mode", ExchangerTool.toolModes.Wall.name());
            tagCompound.setInteger("range", 1);
            stack.setTagCompound(tagCompound);
            NBTTagCompound stateTag = new NBTTagCompound();
            NBTUtil.writeBlockState(stateTag, Blocks.AIR.getDefaultState());
            tagCompound.setTag("blockstate", stateTag);
            NBTTagList coords = new NBTTagList();
            tagCompound.setTag("anchorcoords", coords);
        }
        return tagCompound;
    }

    public void setAnchor(ItemStack stack, ArrayList<BlockPos> coordinates) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }
        NBTTagList coords = new NBTTagList();
        for (BlockPos coord : coordinates) {
            coords.appendTag(NBTUtil.createPosTag(coord));
        }
        tagCompound.setTag("anchorcoords", coords);
    }

    public ArrayList<BlockPos> getAnchor(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }
        ArrayList<BlockPos> coordinates = new ArrayList<BlockPos>();
        NBTTagList coordList = (NBTTagList) tagCompound.getTag("anchorcoords");
        if (coordList.tagCount() == 0 || coordList == null) { return coordinates;}

        for (int i = 0;i<coordList.tagCount();i++) {
            coordinates.add(NBTUtil.getPosFromTag(coordList.getCompoundTagAt(i)));
        }
        return coordinates;
    }

    public void setToolMode(ItemStack stack, ExchangerTool.toolModes mode) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }
        tagCompound.setString("mode", mode.name());
    }

    public void setToolRange(ItemStack stack, int range) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }
        tagCompound.setInteger("range", range);
    }

    public void setToolBlock(ItemStack stack, IBlockState state) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }
        if (state == null) {state = Blocks.AIR.getDefaultState();}
        NBTTagCompound stateTag = new NBTTagCompound();
        NBTUtil.writeBlockState(stateTag, state);
        tagCompound.setTag("blockstate", stateTag);
    }

    public ExchangerTool.toolModes getToolMode(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }

        return ExchangerTool.toolModes.valueOf(tagCompound.getString("mode"));
    }

    public int getToolRange(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }
        return tagCompound.getInteger("range");
    }

    public IBlockState getToolBlock(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null){
            tagCompound = initToolTag(stack);
        }
        return NBTUtil.readBlockState(tagCompound.getCompoundTag("blockstate"));
    }

    @Override
    public void addInformation(ItemStack stack, World player, List<String> list, ITooltipFlag b) {
        super.addInformation(stack, player, list, b);
        list.add(TextFormatting.DARK_GREEN + "Block: " + getToolBlock(stack).getBlock().getLocalizedName());
        list.add(TextFormatting.AQUA + "Mode: " + getToolMode(stack));
        list.add(TextFormatting.RED + "Range: " + getToolRange(stack));

    }


    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        if (!world.isRemote) {
            if (player.isSneaking()) {
                selectBlock(stack, player);
            } else {
                exchange(player,stack);
            }
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        if (!world.isRemote) {
            if (player.isSneaking()) {
                selectBlock(itemstack, player);
            } else {
                exchange(player,itemstack);
            }
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
    }

    private void selectBlock(ItemStack stack, EntityPlayer player) {
        World world = player.world;
        RayTraceResult lookingAt = getLookingAt(player);
        if (lookingAt == null) {return;}
        BlockPos pos = lookingAt.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        TileEntity te = world.getTileEntity(pos);
        if (te != null) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED + "Invalid Block"), true);
            return;
        }
        if (state != null) {
            setToolBlock(stack,state);
        }
    }

    public void toggleMode(EntityPlayer player, ItemStack heldItem) {
        ExchangerTool.toolModes mode = getToolMode(heldItem);
        mode = mode.next();
        setToolMode(heldItem,mode);
        player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "Tool Mode: " + mode.name()), true);
    }

    public void rangeChange(EntityPlayer player, ItemStack heldItem) {
        int range = getToolRange(heldItem);
        if (range >= Config.maxRange) {
            range = 1;
        } else {
            range++;
        }
        setToolRange(heldItem,range);
        player.sendStatusMessage(new TextComponentString(TextFormatting.DARK_BLUE + "Tool range: " + range), true);
    }

    public RayTraceResult getLookingAt(EntityPlayer player) {
        World world = player.world;
        Vec3d look = player.getLookVec();
        Vec3d start = new Vec3d(player.posX,player.posY+player.getEyeHeight(),player.posZ);
        Vec3d end = new Vec3d(player.posX+look.x * rayTraceRange,player.posY+player.getEyeHeight()+look.y*rayTraceRange,player.posZ+look.z*rayTraceRange);
        return world.rayTraceBlocks(start,end,false,true,false);
    }

    public boolean anchorBlocks(EntityPlayer player, ItemStack stack) {
        World world = player.world;
        int range = getToolRange(stack);
        ExchangerTool.toolModes mode = getToolMode(stack);
        ArrayList<BlockPos> currentCoords = getAnchor(stack);

        if (currentCoords.size() == 0) {
            //If we don't have an anchor, find the block we're supposed to anchor to
            RayTraceResult lookingAt = getLookingAt(player);
            //If we aren't looking at anything, exit
            if (lookingAt == null) {return false;}
            BlockPos startBlock = lookingAt.getBlockPos();
            EnumFacing sideHit = lookingAt.sideHit;
            IBlockState setBlock = getToolBlock(stack);
            //If we are looking at air, exit
            if (startBlock == null || world.getBlockState(startBlock) == Blocks.AIR.getDefaultState()) {return false;}
            //Build the positions list based on tool mode and range
            ArrayList<BlockPos> coords = ExchangingModes.getBuildOrders(world, player, startBlock, sideHit, range, mode,setBlock);
            //Set the anchor NBT
            setAnchor(stack, coords);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "Render Anchored"), true);
        } else {
            //If theres already an anchor, remove it.
            setAnchor(stack, new ArrayList<BlockPos>());
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "Anchor Removed"), true);
        }
        return true;
    }

    public boolean exchange(EntityPlayer player, ItemStack stack) {
        World world = player.world;
        int range = getToolRange(stack);
        ExchangerTool.toolModes mode = getToolMode(stack);
        ArrayList<BlockPos> coords = getAnchor(stack);

        if (coords.size() == 0) {
            //If we don't have an anchor, build in the current spot
            RayTraceResult lookingAt = getLookingAt(player);
            //If we aren't looking at anything, exit
            if (lookingAt == null) {return false;}
            BlockPos startBlock = lookingAt.getBlockPos();
            EnumFacing sideHit = lookingAt.sideHit;
            IBlockState setBlock = getToolBlock(stack);
            coords = ExchangingModes.getBuildOrders(world, player, startBlock, sideHit, range, mode,setBlock);
        } else {
            //If we do have an anchor, erase it (Even if the build fails)
            setAnchor(stack,new ArrayList<BlockPos>());
        }
        Set<BlockPos> coordinates = new HashSet<BlockPos>(coords);
        ItemStack heldItem = player.getHeldItemMainhand();
        IBlockState blockState = getToolBlock(heldItem);

        if (blockState != Blocks.AIR.getDefaultState()) {  //Don't attempt a build if a block is not chosen -- Typically only happens on a new tool.
            IBlockState state = Blocks.AIR.getDefaultState(); //Initialize a new State Variable for use in the fake world
            fakeWorld.setWorldAndState(player.world, blockState, coordinates); // Initialize the fake world's blocks
            for (BlockPos coordinate : coords) {
                if (fakeWorld.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
                    try {
                        //Get the state of the block in the fake world (This lets fences be connected, etc)
                        state = blockState.getActualState(fakeWorld, coordinate);
                    } catch (Exception var8) {
                    }
                }
                //Get the extended block state in the fake world
                //Disabled to fix Chisel
                //state = state.getBlock().getExtendedState(state, fakeWorld, coordinate);
                exchangeBlock(world, player, coordinate, state);
                }
            }
        return true;
    }

    public boolean exchangeBlock(World world, EntityPlayer player, BlockPos pos, IBlockState setBlock) {
        IBlockState currentBlock = world.getBlockState(pos);
        ItemStack itemStack = setBlock.getBlock().getPickBlock(setBlock,null,world,pos,player);
        ItemStack returnItem = currentBlock.getBlock().getPickBlock(currentBlock, null, world,pos,player);
        if (InventoryManipulation.giveItem(returnItem, player)) {
            if (InventoryManipulation.useItem(itemStack,player)) {
                world.spawnEntity(new BlockBuildEntity(world, pos, player, setBlock, 3));
            }
            else {
                InventoryManipulation.useItem(returnItem,player);
            }
        }
        return true;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 20;
    }

    @SideOnly(Side.CLIENT)
    public void renderOverlay(RenderWorldLastEvent evt, EntityPlayer player, ItemStack stack) {
        int range = getToolRange(stack);
        ExchangerTool.toolModes mode = getToolMode(stack);
        RayTraceResult lookingAt = player.rayTrace(20, 1.0F);
        IBlockState state = Blocks.AIR.getDefaultState();
        if (lookingAt != null) {
            World world = player.world;
            IBlockState startBlock = world.getBlockState(lookingAt.getBlockPos());
            ArrayList<BlockPos> coordinates = getAnchor(stack);
            if (((startBlock != null) && (startBlock != Blocks.AIR.getDefaultState()) && (startBlock != ModBlocks.effectBlock.getDefaultState())) || coordinates.size() > 0) {
                //Get the item stack and the block that we'll be rendering (From the Itemstack's NBT)
                ItemStack heldItem = player.getHeldItemMainhand();
                IBlockState renderBlockState = getToolBlock(heldItem);

                //Don't render anything if theres no block selected (Air)
                if (renderBlockState == Blocks.AIR.getDefaultState()) {
                    return;
                }
                //Build a list of coordinates based on the tool mode and range
                if (coordinates.size() == 0) {
                    coordinates = ExchangingModes.getBuildOrders(world, player, lookingAt.getBlockPos(), lookingAt.sideHit, range, mode, renderBlockState);
                }

                //Figure out how many of the block we're rendering we have in the inventory of the player.
                ItemStack itemStack = renderBlockState.getBlock().getPickBlock(renderBlockState,null,world,new BlockPos(0,0,0),player);
                int hasBlocks = InventoryManipulation.countItem(itemStack,player);
                int tempHasBlocks = hasBlocks;

                //Prepare the block rendering
                BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
                BlockRenderLayer origLayer = MinecraftForgeClient.getRenderLayer();

                //Prepare the fake world -- using a fake world lets us render things properly, like fences connecting.
                Set<BlockPos> coords = new HashSet<BlockPos>(coordinates);
                fakeWorld.setWorldAndState(player.world,renderBlockState,coords);

                //Calculate the players current position, which is needed later
                double doubleX = player.lastTickPosX + (player.posX - player.lastTickPosX) * evt.getPartialTicks();
                double doubleY = player.lastTickPosY + (player.posY - player.lastTickPosY) * evt.getPartialTicks();
                double doubleZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * evt.getPartialTicks();

                //Save the current position thats being rendered (I think)
                GlStateManager.pushMatrix();
                //Enable Blending (So we can have transparent effect)
                GlStateManager.enableBlend();
                //This blend function allows you to use a constant alpha, which is defined later
                GlStateManager.blendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA);


                for (BlockRenderLayer layer : LAYERS) {
                    //Technically we are rendering these blocks in all 4 render Layers
                    //I'm not sure why but its the only way it works properly
                    ForgeHooksClient.setRenderLayer(layer);


                    for (BlockPos coordinate : coordinates) {
                        //Push matrix again just because
                        GlStateManager.pushMatrix();
                        //The render starts at the player, so we subtract the player coords and move the render to 0,0,0
                        GlStateManager.translate(-doubleX, -doubleY, -doubleZ);
                        //Now move the render position to the coordinates we want to render at
                        GlStateManager.translate(coordinate.getX(), coordinate.getY(), coordinate.getZ());
                        //Rotate it because i'm not sure why but we need to
                        GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                        //Slightly Larger block to avoid z-fighting.
                        GlStateManager.translate(-0.005f,-0.005f,0.005f);
                        GlStateManager.scale(1.01f, 1.01f, 1.01f);
                        //Set the alpha of the blocks we are rendering -- 1/4 of what we really want because we render 4x over
                        GL14.glBlendColor(1F, 1F, 1F, 0.17f);
                        //Get the block state in the fake world
                        if (fakeWorld.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
                            try {
                                state = renderBlockState.getActualState(fakeWorld, coordinate);
                            } catch (Exception var8) {
                            }
                        }
                        //Get the extended block state in the fake world
                        //Disabled to fix chisel, not sure why.
                        //state = state.getBlock().getExtendedState(state, fakeWorld, coordinate);
                        //Render the defined block
                        dispatcher.renderBlockBrightness(state,1f);
                        tempHasBlocks--;
                        if (tempHasBlocks <0) {
                            GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                            dispatcher.renderBlockBrightness(Blocks.STAINED_GLASS.getDefaultState().withProperty(COLOR, EnumDyeColor.RED), 1f);
                        }
                        //Move the render position back to where it was
                        GlStateManager.popMatrix();
                    }
                    tempHasBlocks = hasBlocks;
                }
                //Set blending back to the default mode
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                ForgeHooksClient.setRenderLayer(origLayer);
                //Disable blend
                GlStateManager.disableBlend();
                RenderHelper.enableStandardItemLighting();
                //Pop from the original push in this method
                GlStateManager.popMatrix();
            }
        }
    }
}