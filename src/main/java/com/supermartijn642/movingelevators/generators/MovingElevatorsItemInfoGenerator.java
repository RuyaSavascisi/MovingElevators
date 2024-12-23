package com.supermartijn642.movingelevators.generators;

import com.supermartijn642.core.generator.ItemInfoGenerator;
import com.supermartijn642.core.generator.ResourceCache;
import com.supermartijn642.movingelevators.MovingElevators;

/**
 * Created 23/12/2024 by SuperMartijn642
 */
public class MovingElevatorsItemInfoGenerator extends ItemInfoGenerator {

    public MovingElevatorsItemInfoGenerator(ResourceCache cache){
        super("movingelevators", cache);
    }

    @Override
    public void generate(){
        this.simpleInfo(MovingElevators.elevator_block, "item/elevator_block");
        this.simpleInfo(MovingElevators.display_block, "item/display_block");
        this.simpleInfo(MovingElevators.button_block, "item/button_block");
    }
}
