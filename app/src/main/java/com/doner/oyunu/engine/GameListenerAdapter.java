package com.doner.oyunu.engine;

/** Boş uygulama; yalnızca ilgilendiğiniz olayları ezin. */
public class GameListenerAdapter implements GameListener {
    @Override public void onDayStarted(int day, DayConfig config) {}
    @Override public void onCustomerArrived(Order order) {}
    @Override public void onOrderSelected(Order order) {}
    @Override public void onNextStepHint(Order order, Step step) {}
    @Override public void onStepStarted(Order order, Step step, int slot, float seconds) {}
    @Override public void onStepFinished(Order order, Step step, boolean orderComplete) {}
    @Override public void onWrongStation(Order order, Station tapped, Station expected) {}
    @Override public void onNoFreeSlot(Order order) {}
    @Override public void onOrderReady(Order order) {}
    @Override public void onOrderDelivered(Order order, int money, int tip, boolean happy) {}
    @Override public void onPatienceWarning(Order order, int level) {}
    @Override public void onCustomerLeft(Order order) {}
    @Override public void onStarsChanged(double oldStars, double newStars) {}
    @Override public void onOutOfStock(Order order, Ingredient ingredient) {}
    @Override public void onEmergencyPurchase(Ingredient ingredient, int qty, int cost) {}
    @Override public void onNotEnoughMoney(int needed) {}
    @Override public void onDayFinished(DaySummary summary) {}
    @Override public void onDayFailed(DaySummary summary) {}
}
