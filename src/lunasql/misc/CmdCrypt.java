package lunasql.misc;

import static joptsimple.util.RegexMatcher.regex;

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.NoSuchPaddingException;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lunasql.cmd.Instruction;
import lunasql.lib.Contexte;
import lunasql.lib.Security;
import lunasql.val.Valeur;
import lunasql.val.ValeurDef;

/**
 * Commande CRYPT <br>
 * (Interne) Chiffre le message donné clef de 128 bits
 * @author M.P.
 */
public class CmdCrypt extends Instruction {

   private final OptionParser parser;

   public CmdCrypt(Contexte cont) {
      super(cont, TYPE_CMDPLG, "CRYPT", null);
      // Option parser
      parser = new OptionParser();
      parser.allowsUnrecognizedOptions();
      parser.accepts("?", "aide sur la commande");
      parser.accepts("k", "clef de chiffrement").withRequiredArg().ofType(String.class)
         .describedAs("clef");
      parser.accepts("p", "mot de passe").withRequiredArg().ofType(String.class)
            .describedAs("str");
      parser.accepts("i", "vecteur d'initialisation").withRequiredArg().ofType(String.class)
         .describedAs("IV");
      parser.accepts("r", "format de la clef").withRequiredArg().ofType(String.class)
         .defaultsTo("hex").describedAs("format").withValuesConvertedBy(regex("hex|b64|utf"));
      parser.accepts("t", "format de sortie").withRequiredArg().ofType(String.class)
         .defaultsTo("hex").describedAs("format").withValuesConvertedBy(regex("hex|b64|utf"));
      parser.accepts("u", "format du vecteur d'init.").withRequiredArg().ofType(String.class)
         .defaultsTo("hex").describedAs("format").withValuesConvertedBy(regex("hex|b64|utf"));
      parser.accepts("a", "algo. de chiffrement").withRequiredArg().ofType(String.class)
         .defaultsTo("AES").describedAs("algo")
         .withValuesConvertedBy(regex("RC4|AES|Blowfish|DES|DESede"));
      parser.accepts("s", "message découpé").withRequiredArg().ofType(Integer.class)
         .describedAs("nbcar");
      parser.accepts("f", "chiffrer un fichier").withRequiredArg().ofType(File.class)
            .describedAs("file");
      parser.nonOptions("chaîne").ofType(String.class);
   }

   @Override
   public int execute() {
      OptionSet options;
      try {
         options = parser.parse(getCommandA1());
         // Aide sur les options
         if (options.has("?")) {
            parser.printHelpOn(cont.getWriterOrOut());
            cont.setValeur(null);
            return RET_CONTINUE;
         }
      }
      catch (OptionException|IOException ex) {
         return cont.exception("CRYPT", "erreur d'option : " + ex.getMessage(), lng, ex);
      }

      // Exécution avec autres options
      try {
         Valeur vr = new ValeurDef(cont);
         Security sec = new Security();
         sec.setCipherAlgo((String) options.valueOf("a"));
         String fkey = (String) options.valueOf("r"), sk;
         byte[] secret;
         if (options.has("k")) {
            sk = (String) options.valueOf("k");
            secret = "b64".equals(fkey) ? Security.b64decode(sk) :
                  ("hex".equals(fkey) ? Security.hexdecode(sk) : sk.getBytes());
            if (secret == null)
               return cont.erreur("CRYPT", "format de clef invalide (hex|b64|utf attendu)", lng);
            sec.setSecretKey(secret);
         }
         else if (options.has("p")) sec.setSecretString((String) options.valueOf("p"));
         else return cont.erreur("CRYPT", "clef ou mot de passe de chiffrement manquant (-k / -p)", lng);

         // Chiffrement
         String val;
         if (options.has("f")) {
            File fpla = (File) options.valueOf("f"), fcry = new File(fpla.getAbsolutePath() + ".cry");
            if (!cont.askWriteFile(fcry)) {
               cont.setVar(Contexte.ENV_CMD_STATE, Contexte.STATE_FALSE);
               return RET_CONTINUE;
            }

            val = Long.toString(sec.crypt(fpla, fcry));
         }
         else {
            String fiv  = (String) options.valueOf("u"), iv = (String) options.valueOf("i"),
                   fchi = (String) options.valueOf("t");
            byte[] vinit = "b64".equals(fiv) ? Security.b64decode(iv)
                  : ("hex".equals(fiv) ? Security.hexdecode(iv) : (iv == null ? null : iv.getBytes()));
            if (vinit == null)
               return cont.erreur("CRYPT", "VI absent ou format invalide (hex|b64|utf attendu)", lng);

            val = sec.crypt(listToString(options.nonOptionArguments()), iv, fiv, fchi,
                            options.has("s") ? ((Integer)options.valueOf("s")) : 0);
         }
         // Retour
         vr.setSubValue(val);
         vr.setRet();
         cont.setVar(Contexte.ENV_CMD_STATE, Contexte.STATE_FALSE);
         cont.setValeur(vr);
         return RET_CONTINUE;
      }
      catch (IllegalArgumentException ex) {
         return cont.erreur("CRYPT", ex.getMessage(), lng);
      }
      catch (NoSuchAlgorithmException|NoSuchPaddingException|InvalidKeySpecException|InvalidKeyException|
             InvalidAlgorithmParameterException|IOException ex){
         return cont.erreur("CRYPT", "ERREUR " + ex.getClass().getSimpleName() + " : " +
               ex.getMessage() , lng);
      }
   }

   /*
    * (non-Javadoc)
    * @see lunasql.cmd.Instruction#getHelp()
    */
   @Override
   public String getDesc(){
      return "  crypt       Chiffre un message par clef de 128 bits\n";
   }
}// class
