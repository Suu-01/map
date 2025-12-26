package com.du.gis_project;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class GisProjectApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(GisProjectApplication.class, args);

        System.out.println("CTX = " + ctx.getClass().getName());
    }

}
