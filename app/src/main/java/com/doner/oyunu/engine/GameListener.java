package com.doner.oyunu.engine;

/** Motor olayları. Tüm çağrılar tick()/eylem metodunu çağıran iş parçacığında yapılır. */
public interface GameListener {
    void onDayStarted(int day, DayConfig config);
    void onCustomerArrived(Order order);
    void onOrderSelected(Order order);
    void onNextStepHint(Order order, Step step);
    void onStepStarted(Order order, Step step, int slot, float seconds);
    void onStepFinished(Order order, Step step, boolean orderComplete);
    void onWrongStation(Order order, Station tapped, Station expected);
    void onNoFreeSlot(Order order);
    void onOrderReady(Order order);
    void onOrderDelivered(Order order, int money, int tip, boolean happy);
    void onPatienceWarning(Order order, int level);
    void onCustomerLeft(Order order);
    void onStarsChanged(double oldStars, double newStars);
    void onOutOfStock(Order order, Ingredient ingredient);
    void onEmergencyPurchase(Ingredient ingredient, int qty, int cost);
    void onNotEnoughMoney(int needed);
    void onDayFinished(DaySummary summary);
    void onDayFailed(DaySummary summary);
}
