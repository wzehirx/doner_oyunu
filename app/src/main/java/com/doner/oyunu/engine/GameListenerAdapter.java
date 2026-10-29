package com.doner.oyunu.engine;

/** Boş uygulama; yalnızca ilgilendiğiniz olayları ezin. */
public class GameListenerAdapter implements GameListener {
    @Override public void onPrepPhaseStarted(int day) {}
    @Override public void onPrepPhaseEnded(int day) {}
    @Override public void onDayStarted(int day, DayConfig config) {}
    @Override public void onDayFinished(DaySummary summary) {}
    @Override public void onDayFailed(DaySummary summary) {}
    @Override public void onCustomerArrived(Order order) {}
    @Override public void onOrderSelected(Order order) {}
    @Override public void onItemSelected(Order order, int itemIndex) {}
    @Override public void onNextStepHint(Order order, Step step) {}
    @Override public void onPatienceWarning(Order order, int level) {}
    @Override public void onCustomerLeft(Order order) {}
    @Override public void onStepStarted(Order order, Step step, int slot, float seconds) {}
    @Override public void onStepFinished(Order order, Step step, boolean orderComplete) {}
    @Override public void onWrongStation(Order order, Station tapped, Station expected) {}
    @Override public void onStepLocked(Order order, Step step) {}
    @Override public void onNoFreeSlot(Order order) {}
    @Override public void onItemFinished(Order order, int itemIndex) {}
    @Override public void onOrderReadyToPack(Order order) {}
    @Override public void onOrderReady(Order order) {}
    @Override public void onOrderDelivered(Order order, int money, int tip, boolean happy) {}
    @Override public void onToppingSelectionNeeded(Order order, int itemIndex) {}
    @Override public void onToppingsRejected(Order order, int itemIndex, StartResult reason, Ingredient ingredient) {}
    @Override public void onOrderMistake(Order order, ToppingCheck check, double penalty) {}
    @Override public void onOrderPerfect(Order order) {}
    @Override public void onPrepStarted(PrepKind kind, boolean byApprentice, float seconds) {}
    @Override public void onPrepFinished(PrepKind kind, boolean byApprentice, int amount) {}
    @Override public void onPrepRejected(PrepKind kind, PrepResult reason, Ingredient ingredient) {}
    @Override public void onApprenticeAction(ApprenticeAction action) {}
    @Override public void onStarsChanged(double oldStars, double newStars) {}
    @Override public void onOutOfStock(Order order, Ingredient ingredient) {}
    @Override public void onEmergencyPurchase(Ingredient ingredient, int qty, int cost) {}
    @Override public void onNotEnoughMoney(int needed) {}
    @Override public void onToppingAddStarted(Order order, int itemIndex, Ingredient ingredient, int count, float seconds) {}
    @Override public void onToppingAdded(Order order, int itemIndex, Ingredient ingredient, int count) {}
    @Override public void onToppingRemoved(Order order, int itemIndex, Ingredient ingredient, int remaining) {}
    @Override public void onToppingsFinished(Order order, int itemIndex, java.util.Set<String> chosen) {}
    @Override public void onSizeChosen(Order order, int itemIndex, PortionSize ordered, PortionSize chosen) {}
    @Override public void onStationInfo(Order order, int itemIndex, String optionId) {}
    @Override public void onOptionRejected(Order order, String optionId, StartResult reason) {}
}
