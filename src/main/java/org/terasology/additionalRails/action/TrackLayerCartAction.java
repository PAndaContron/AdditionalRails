package org.terasology.additionalRails.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.additionalRails.components.TrackLayerCartComponent;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.inventory.InventoryComponent;
import org.terasology.logic.inventory.ItemComponent;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.math.geom.Vector3i;
import org.terasology.minecarts.blocks.RailComponent;
import org.terasology.minecarts.blocks.RailsUpdateFamily;
import org.terasology.minecarts.components.RailVehicleComponent;
import org.terasology.registry.In;
import org.terasology.segmentedpaths.components.PathFollowerComponent;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.items.BlockItemComponent;

import java.util.EnumSet;
import java.util.List;

/**
 * System covering Track Layer Cart's behavior.
 * @author anuar2k
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class TrackLayerCartAction extends BaseComponentSystem implements UpdateSubscriberSystem {

    @In
    EntityManager entityManager;
    @In
    WorldProvider worldProvider;

    @Override
    public void update(float delta) {
        //Look for all Track Layer Carts.
        for (EntityRef entity : entityManager.getEntitiesWith(TrackLayerCartComponent.class, RailVehicleComponent.class, PathFollowerComponent.class, InventoryComponent.class)) {
            //Get the rail block under the cart.
            PathFollowerComponent pfComp = entity.getComponent(PathFollowerComponent.class);
            EntityRef rbEntity = pfComp.segmentMeta.association;

            if (!rbEntity.hasComponent(RailComponent.class)) {
                continue;
            }

            BlockComponent bComp = rbEntity.getComponent(BlockComponent.class);
            Vector3i rbLocation = new Vector3i(bComp.getPosition());
            Block rBlock = bComp.getBlock();

            //Get rail's connections - we only want straight, edge rail, which have only one connection.
            byte connections = Byte.parseByte(rBlock.getURI().getIdentifier().toString());
            EnumSet<Side> sides = SideBitFlag.getSides(connections);
            if (sides.size() != 1) {
                continue;
            }
            Side side = sides.iterator().next();
            if (!side.isHorizontal()) {
                continue;
            }

            RailsUpdateFamily ruFamily = (RailsUpdateFamily)rBlock.getBlockFamily();
            //The block we are going to place is the same our cart is currently on, so we can generate it based on the same connection flags.
            rBlock = ruFamily.getBlockByConnection(connections);
            //The location of new rail block is opposite to rail's current connection, so we reverse the side and add its vector to get the location of new rail block we're going to place.
            side = side.reverse();
            Vector3i newRailLocation = rbLocation.add(side.getVector3i());

            //If the there is no scape for new rail - go to another cart entity.
            Block nextBlock = worldProvider.getBlock(newRailLocation);
            if (!nextBlock.getURI().equals(BlockManager.AIR_ID)) {
                continue;
            }

            //We have to do the same if there is no block on which rail would lay on.
            Block underNextBlock = worldProvider.getBlock(new Vector3i(newRailLocation).add(Vector3i.down()));
            if (underNextBlock.isPenetrable() || underNextBlock.isLiquid()) {
                continue;
            }

            //Now we're checking if rail's inventory has some rails to be placed.
            InventoryComponent iComponent = entity.getComponent(InventoryComponent.class);
            List<EntityRef> slots = iComponent.itemSlots;

            boolean gotItem = false;
            for (EntityRef slot : slots) {
                //If it's not a rail block - go to next slot.
                if (slot == EntityRef.NULL) {
                    continue;
                }
                if (!slot.hasComponent(ItemComponent.class)) {
                    continue;
                }
                if (!slot.hasComponent(BlockItemComponent.class)) {
                    continue;
                }
                ItemComponent item = slot.getComponent(ItemComponent.class);
                BlockItemComponent bitem = slot.getComponent(BlockItemComponent.class);
                //If the block item is a rail block...
                if (bitem.blockFamily.equals(ruFamily)) {
                    item.stackCount--;
                    gotItem = true;
                    //If the stack's count is equal or lower than zero, remove the stack from inventory.
                    if (item.stackCount <= 0) {
                        iComponent.itemSlots.set(iComponent.itemSlots.indexOf(slot), EntityRef.NULL);
                    }
                    break;
                }
            }

            //Place the new rail if it was available in the inventory.
            if (gotItem) {
                worldProvider.setBlock(newRailLocation, rBlock);
            }
        }
    }

}
