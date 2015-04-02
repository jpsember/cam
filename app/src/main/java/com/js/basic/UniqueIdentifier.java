package com.js.basic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for testing and development purposes that assigns a unique identifier
 * to an object. Identifiers are guaranteed to be unique, and the class is
 * threadsafe.
 */
class UniqueIdentifier {

  /**
   * Given an object, return a unique human-readable string derived from its
   * hashcode
   */
  public static String nameFor(Object object) {
    if (object == null)
      return "<null>";
    String name;
    int hashCode = object.hashCode();
    synchronized (UniqueIdentifier.class) {
      if (sNamePrefixes == null) {
        buildNameMap();
      }
      name = sObjectNames.get(hashCode);
      if (name == null) {
        int index = sObjectNames.size();
        PrefixEntry entry = sNamePrefixes.get(index
            % sNamePrefixes.size());
        int cell = entry.allocMember();
        name = entry.mName;
        if (cell != 0)
          name = name + cell;
        sObjectNames.put(hashCode, name);
      }
    }
    return name;
  }

  @Override
  public String toString() {
    // Assign this object a unique integer id, if it hasn't got one already
    if (mId == 0) {
      synchronized (UniqueIdentifier.class) {
        sPreviousIdentifier++;
        mId = sPreviousIdentifier;
      }
    }
    return nameFor(mId);
  }

  private static void buildNameMap() {
    String THREE_LETTER_WORDS =
        "ASKBOWCOBDOEEATFAYGABHOGIVYJOBKOILILMACNAGOVAPALRIGSAGTARURNVOXWANYAH" +
            "ZITACEBUTCONDAPELFFROGARHATISMJAMKEYLIPMOONANOWLPAYRAGSATTABUGHVANWET" +
            "YAKZAGAMPBIBCAWDIPEELFENGETHUBIONJOTKIDLEGMOWNOBOARPANRANSAPTADUSEVIA" +
            "WHYYUKZOOADDBUYCUDDUGEARFITGYMHEHILLJUTKENLIBMATNAHOUTPIXRUBSALTAGUMP" +
            "VIMWEEYAWZIGAPEBOACOWDEBEGGFOGGUNHMMILKJAYKEGLOWMETNIBOOHPAXRADSAWTUB" +
            "VETWAXYEPZIPAIRBAHCOYDENEONFORGUYHOEIMPJOEKITLOUMIDNIPODDPINROBSAMTSK" +
            "VATWHOYUPZENASSBAYCOTDIEEREFIEGAPHOYIRKJEWKAYLAYMARNTHOILPOWROESIPTAW" +
            "VEXWASYEWZAPARKBOXCURDONELMFEWGOOHUGINKJOGKIPLETMEWNONOREPAPRYESETTEE" +
            "VIEWAYYOUZOEARCBOPCODDIGEWEFOPGOTHEMIREJIGKINLOBMOPNILOPTPADROTSLYTHO" +
            "VOWWITYAMADZBETCOGDUBEGOFEMGASHAYINNJAGKATLEIMANNODOLEPICRIBSEETOMVEG" +
            "WONYONADOBUDCUTDEWELSFARGUSHEYICEJETLADMUGNAYOWNPIGRUNSISTOPWRYYINALE" +
            "BODCAMDIDEVEFAXGAGHIDITSJABLITMODNAPOAKPEARAHSHHTOYWADYETASHBIOCARDOC" +
            "EKEFOXGINHISIDAJUGLAXMUMNABOATPIPRIDSPYTAMWEBYIPAVEBOGCALDAMETCFEDGUT" +
            "HERICYJARLYEMOMNEWONEPOIRIMSUETHYWOWYENAIDBINCOPDUHELKFINGODHOPJOYLAW" +
            "MENNOTOAFPEWROMSUNTEAWAGYAPAMYBOBCANDAYERAFLYGOBHITJAWLOTMUDNIXOWEPAT" +
            "ROWSOTTILWOOYUMASPBAMCOODOWELLFEEGUMHAGJIBLABMAYNEDOFTPUSRODSHETISWOK" +
            "YEHAUKBEACELDOGEBBFATGALHAWLAGMAPNOROFFPIERAWSODTEDWARYEAAPTBATCADDIM" +
            "EZOFUNGIGHOBLUXMRSNOWOHMPUBREDSHYTONWINYESANDBUSCAYDABEMUFLUGATHOTLOP" +
            "MADNITOLDPUTRIPSONTOGWOEAGOBEDCATDRYERGFURGAYHEWLOXMIXNETODEPRYRAMSEA" +
            "TAXWIZARTBANCAPDUEEYEFANGELHUTLOGMOBOURPARRUMSAXTIPWEDATMBOTCUBDUDEND" +
            "FOEGEMHIMLIEMAXPLYRUTSEXTUXWIGAGEBADCRYDYEFIRGEEHAMLEEMAWPAMRAPSKATOW" +
            "ANYBIGCABDOTFOBGADHEXLIDPETRUGSOXTANALLBRACUPDINFADGAMHUELUGPOTRONSEW" +
            "TAPARMBUBCUEDUNFIGGNUHOWLAPPUGREPSOBTOEAFTBUMDOPFIBHODLEDPENRAYSOPTEN" +
            "ANTBOODUOFIXHASLAMPOPRATSIXTRYAREBEGDADFRYHIEPUPREXSOLTUTAIMBUGFEZHUH" +
            "PEGRUESANTITAVABEEHIPPOXREFSITTICANNBAGHENPERSINTATAXEBIZHUMPUNSTYTWO" +
            "AWEBARHAHPODSUPTOTAHABIDPITSAKTINATEBYEPAWSPATIEAILBITPEPSOYTUGAYEBUN" +
            "PROSUBTHEAWLBOYSKITOOACTBONSKYSAYSUMSIRSOWSAD";

    sNamePrefixes = new ArrayList();

    for (int i = 0; i < THREE_LETTER_WORDS.length(); i += 3) {
      String prefix = THREE_LETTER_WORDS.substring(i, i + 3);
      sNamePrefixes.add(new PrefixEntry(prefix));
    }
  }

  private static class PrefixEntry {
    PrefixEntry(String name) {
      mName = name;
    }

    int allocMember() {
      return mPopulation++;
    }

    String mName;
    int mPopulation;
  }

  private static List<PrefixEntry> sNamePrefixes;
  private static volatile Map<Integer, String> sObjectNames = new HashMap();
  private static int sPreviousIdentifier;

  private int mId;
}
