// src/main/java/com/osman/traviaskbot/entity/Route.java
package com.osman.traviaskbot.entity;

public class Route {

    private String routeName; // Rota ismi
    private double distance;  // Rota mesafesi

    // Constructor
    public Route(String routeName, double distance) {
        this.routeName = routeName;
        this.distance = distance;
    }

    // Getter ve Setter
    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
}
