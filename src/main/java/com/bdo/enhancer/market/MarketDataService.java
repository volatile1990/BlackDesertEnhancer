package com.bdo.enhancer.market;

import com.bdo.enhancer.model.item.Accessory;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Service-Klasse, die den Zugriff auf Marktdaten kapselt
 */
@Setter
public class MarketDataService {
    
    private BDOMarketConnector marketConnector;
    private Consumer<String> progressCallback;
    
    public MarketDataService() {
        this.marketConnector = new BDOMarketConnector();
    }
    
    /**
     * Holt alle Accessoires vom Markt
     * 
     * @return Liste von Accessoires mit aktuellen Preisen
     */
    public List<Accessory> getAccessories() {
        if (progressCallback != null) {
            marketConnector.setProgressCallback(progressCallback);
        }
        return marketConnector.getAccessories();
    }
    
    /**
     * Setzt den Callback für Fortschrittsmeldungen
     * 
     * @param callback Callback-Funktion für Statusmeldungen
     */
    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
        if (marketConnector != null) {
            marketConnector.setProgressCallback(callback);
        }
    }
}
