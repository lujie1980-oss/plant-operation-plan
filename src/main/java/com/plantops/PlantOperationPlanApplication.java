package com.plantops;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class PlantOperationPlanApplication {

    public static void main(String[] args) {
        Quarkus.run(args);
    }
}
