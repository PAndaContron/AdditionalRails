/*
 * Copyright 2018 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.additionalRails.action;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.additionalRails.components.TrackLayerCartComponent;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.inventory.InventoryComponent;
import org.terasology.logic.inventory.ItemComponent;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.math.geom.Vector3i;
import org.terasology.minecarts.blocks.RailBlockFamily;
import org.terasology.minecarts.blocks.RailComponent;
import org.terasology.minecarts.components.RailVehicleComponent;
import org.terasology.registry.In;
import org.terasology.segmentedpaths.components.PathFollowerComponent;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.items.BlockItemComponent;

import com.google.common.collect.Sets;

/**
 * System covering Track Layer Cart's behavior.
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class TrackLayerCartAction extends BaseComponentSystem implements UpdateSubscriberSystem {
	Logger LOGGER = LoggerFactory.getLogger(TrackLayerCartAction.class);

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
            Vector3i rbLocation = new Vector3i(bComp.position);
            Block rBlock = bComp.block;
            
            Vector3i heading = new Vector3i(pfComp.heading, RoundingMode.CEILING);
            
            //Only work with straight tracks
            if (heading.getX() != 0 && heading.getY() != 0) {
            	continue;
            }

            Side side = Side.inDirection(heading.getX(), 0, heading.getZ());

            RailBlockFamily ruFamily = (RailBlockFamily) rBlock.getBlockFamily();
            Vector3i newRailLocation = new Vector3i(rbLocation).add(side.getVector3i());
            rBlock = ruFamily.getBlockForPlacement(newRailLocation, null, null);
            layTrack(entity, ruFamily, newRailLocation);
            side = side.reverse();
            newRailLocation = new Vector3i(rbLocation).add(side.getVector3i());
            rBlock = ruFamily.getBlockForPlacement(newRailLocation, null, null);
            layTrack(entity, ruFamily, newRailLocation);
        }
    }
    
    public void layTrack(EntityRef cart, RailBlockFamily ruFamily, Vector3i newRailLocation) {
    	Block rBlock = ruFamily.getBlockForPlacement(newRailLocation, null, null);

        //If the there is no space for new rail - go to another cart entity.
        Block nextBlock = worldProvider.getBlock(newRailLocation);
        if (!nextBlock.getURI().equals(BlockManager.AIR_ID)) {
            return;
        }

        //We have to do the same if there is no block on which rail would lay on.
        Block underNextBlock = worldProvider.getBlock(new Vector3i(newRailLocation).add(Vector3i.down()));
        if (underNextBlock.isPenetrable() || underNextBlock.isLiquid()) {
            return;
        }

        //Now we're checking if rail's inventory has some rails to be placed.
        InventoryComponent iComponent = cart.getComponent(InventoryComponent.class);
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
