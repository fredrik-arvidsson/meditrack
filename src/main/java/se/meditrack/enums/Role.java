package se.meditrack.enums;

/**
 * Användarroller i MediTrack.
 *
 * Behörighetsmodellen bygger på separation of duties (HSLF-FS 2017:37):
 * en sjuksköterska som skapar en beställning får inte själv bekräfta den.
 */
public enum Role {
    NURSE,
    PHARMACIST,
    ADMIN
}
