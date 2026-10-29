package com.doner.oyunu.engine;

/** Malzeme seçimi panelindeki bir seçenek (UI düğmesi). */
public final class ToppingOption {
    public final Ingredient ingredient;
    /** Stokta (veya hazır tepside) var; false ise seçilemez. */
    public final boolean available;
    /** Panel açıldığında önceden seçili mi (1-3. gün varsayılan küme, 4. günden itibaren boş). */
    public final boolean selected;
    public final boolean isDefault;
    public final int stock;
    /** Hazır tepsideki kalan kullanım. */
    public final int trayUses;

    ToppingOption(Ingredient ingredient, boolean available, boolean selected, boolean isDefault, int stock, int trayUses) {
        this.ingredient = ingredient;
        this.available = available;
        this.selected = selected;
        this.isDefault = isDefault;
        this.stock = stock;
        this.trayUses = trayUses;
    }
}
