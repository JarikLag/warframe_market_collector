package com.warframemarket.collector.service;

/**
 * Callbacks fired while a refresh job runs. {@link CollectorService} delivers them
 * through the callback executor it was given, so the GUI receives them on the
 * event dispatch thread.
 */
public interface CollectorListener {

    /** The item catalogue was replaced; every table needs rebuilding. */
    void onCatalogChanged();

    /** One item's price snapshot changed. */
    void onItemUpdated(String slug);

    /**
     * @param done      items finished so far
     * @param total     items in the job
     * @param message   short human-readable status line
     */
    void onProgress(int done, int total, String message);

    /** The job ended: finished normally, cancelled, or failed outright. */
    void onFinished(String summary, boolean cancelled, Throwable failure);
}
