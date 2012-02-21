/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprsc;

/**
 * smprsc trick class - only to give Makefile some source to compile.
 * 
 * @author Sylvain LAFRASSE
 */
public class Trick {

    public Trick() {
        System.out.println("Trick()");
    }

    /**
     * Main entry point
     *
     * @param args command line arguments (open file ...)
     */
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public static void main(final String[] args) {
        new Trick();
    }
}
/*___oOo___*/
