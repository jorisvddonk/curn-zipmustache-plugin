package nl.mooses.curn.plugins;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import com.samskivert.mustache.Template.Fragment;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.clapper.curn.*;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.curn.parser.RSSItem;
import org.clapper.util.config.ConfigurationException;

public class MustacheZipperPlugin implements OutputHandler {

    private String name;
    private File outputFile;
    private ArrayList<FeedChannel> feedChannels;
    private CurnConfig curnconfig;
    private ConfiguredOutputHandler configuredoutputhandler;
    private HashMap<String, Integer> varsMap;
    private ArrayList<IncludeFile> includeFiles;
    private ArrayList<MustacheIncludeFile> mustacheIncludeFiles;
    private HashMap<Integer, String> uuids;
    
    
    private class MustacheIncludeFile {
        private File inTemplateFile;
        private String zipLocation;

        public MustacheIncludeFile(String inputTemplateFileLocation, String zipLocation) throws FileNotFoundException {
            this.inTemplateFile = new File(inputTemplateFileLocation);
            if (!this.inTemplateFile.exists()) {
                throw new FileNotFoundException(this.inTemplateFile.getPath());
            }
            this.zipLocation = zipLocation;
        }

        public File getInputTemplateFile() {
            return inTemplateFile;
        }

        public String getZipLocation() {
            return zipLocation;
        }
        
        
    }
    
    private class IncludeFile {
        private File inFile;
        private String zipLocation;

        public IncludeFile(String inFilePath, String zipLocation) throws FileNotFoundException {
            this.inFile = new File(inFilePath);
            this.zipLocation = zipLocation;
            
            if (!inFile.exists()) {
                throw new FileNotFoundException(inFile.getPath());
            }
        }

        public File getInFile() {
            return inFile;
        }

        public String getZipLocation() {
            return zipLocation;
        }
        
    }
    
    private class FeedChannel {
        private RSSChannel channel;
        private FeedInfo feed;

        public FeedChannel(RSSChannel channel, FeedInfo feed) {
            this.channel = channel;
            this.feed = feed;
        }

        public RSSChannel getChannel() {
            return channel;
        }

        public FeedInfo getFeed() {
            return feed;
        }
    }
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String string) throws CurnException {
        name = string;
    }

    @Override
    public void init(CurnConfig cc, ConfiguredOutputHandler coh) throws ConfigurationException, CurnException {
        try {
            includeFiles = new ArrayList<IncludeFile>();
            mustacheIncludeFiles = new ArrayList<MustacheIncludeFile>();
            configuredoutputhandler = coh;
            curnconfig = cc;
            feedChannels = new ArrayList<FeedChannel>();
            varsMap = new HashMap<String, Integer>();
            uuids = new HashMap<Integer, String>();
            outputFile = new File(cc.getConfigurationValue(coh.getOutputHandler().getName(), "OutputFile"));
            
            for (String varName : cc.getVariableNames(coh.getOutputHandler().getName())) {
                if (varName.startsWith("IncludeFile")) {
                    String[] confs = cc.getConfigurationTokens(coh.getOutputHandler().getName(), varName);
                    IncludeFile incl = new IncludeFile(confs[0], confs[1]);
                    includeFiles.add(incl);
                } else if (varName.startsWith("MustacheIncludeFile")) {
                    String[] confs = cc.getConfigurationTokens(coh.getOutputHandler().getName(), varName);
                    MustacheIncludeFile mincl = new MustacheIncludeFile(confs[0], confs[1]);
                    mustacheIncludeFiles.add(mincl);
                }
            };
            
            name = "MustacheZipperPlugin";
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MustacheZipperPlugin.class.getName()).log(Level.SEVERE, null, ex);
            throw new CurnException(ex);
        }
    }

    @Override
    public OutputHandler makeCopy() throws CurnException {
        return new MustacheZipperPlugin();
    }

    @Override
    public void displayChannel(RSSChannel rssc, FeedInfo fi) throws CurnException {
        feedChannels.add(new FeedChannel(rssc, fi));
        RSSItem ri;
    }

    @Override
    public void flush() throws CurnException {
        ZipOutputStream zos = null;
        try {
            final Mustache.Lambda idGenLambda = new Mustache.Lambda() {

                @Override
                public void execute(Fragment frag, Writer out) throws IOException {
                    String key = frag.execute();
                    out.write("" + key.hashCode());
                }
            };
            final Mustache.Lambda LresetVar = new Mustache.Lambda() {

                @Override
                public void execute(Fragment frag, Writer out) throws IOException {
                    String key = frag.execute();
                    Integer i = varsMap.get(key);
                    if (i == null) {
                        i = 0;
                    }
                    varsMap.put(key, i);
                }
                
            };
            final Mustache.Lambda LincgetVar = new Mustache.Lambda() {

                @Override
                public void execute(Fragment frag, Writer out) throws IOException {
                    String key = frag.execute();
                    Integer i = varsMap.get(key);
                    if (i == null) {
                        i = 0;
                    }
                    i += 1;
                    varsMap.put(key, i);
                    out.write("" + i);
                }
                
            };
            final Mustache.Lambda LincGetUUID = new Mustache.Lambda() {

                @Override
                public void execute(Fragment frag, Writer out) throws IOException {
                    Integer key = Integer.valueOf(frag.execute());
                    String uuid = uuids.get(key);
                    if (uuid == null) {
                        uuid = UUID.randomUUID().toString();
                        uuids.put(key, uuid);
                    }
                    out.write(uuid);
                }
                
            };
            Object context = new Object() {
                Object feeds = feedChannels;
                Object cconfig = curnconfig;
                Object coutputhandler = configuredoutputhandler;
                Mustache.Lambda hashcode = idGenLambda;
                Mustache.Lambda resetVar = LresetVar;
                Mustache.Lambda incgetVar = LincgetVar;
                Mustache.Lambda getUUID = LincGetUUID;
            };
            zos = new ZipOutputStream(new FileOutputStream(outputFile));
            OutputStreamWriter osw = new OutputStreamWriter(zos, "UTF-8");
            
            for (MustacheIncludeFile mf : mustacheIncludeFiles) {
                Template template = Mustache.compiler().compile(new FileReader(mf.getInputTemplateFile()));
                writeZipfile(zos, osw, template, mf.getZipLocation(), context);
            }
            for (IncludeFile incl : includeFiles) {
                writeZipfile(zos, osw, incl.getInFile(), incl.getZipLocation());
            }
            zos.close();
        } catch (IOException ex) {
            Logger.getLogger(MustacheZipperPlugin.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                zos.close();
            } catch (IOException ex) {
                Logger.getLogger(MustacheZipperPlugin.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void writeZipfile(ZipOutputStream zos, OutputStreamWriter osw, File fil, String fileLocation) throws IOException {
            ZipEntry ze = new ZipEntry(fileLocation);
            zos.putNextEntry(ze);            
            BufferedReader br  = new BufferedReader(new FileReader(fil));
            while(br.ready()) {
                osw.write(br.read());
            }
            br.close();
            osw.flush();
            zos.closeEntry();
    }
    
    private void writeZipfile(ZipOutputStream zos, OutputStreamWriter osw, Template template, String fileLocation, Object context) throws IOException {
            ZipEntry ze = new ZipEntry(fileLocation);
            zos.putNextEntry(ze);            
            template.execute(context, osw);
            osw.flush();
            zos.closeEntry();
    }

    @Override
    public String getContentType() {
        return "application/x-kobo-epub"; //TODO: proper mimetype; make it configurable...s
    }

    @Override
    public File getGeneratedOutput() throws CurnException {
        System.out.println("GETGENERATEDOUTPUT");
        return null;
    }

    @Override
    public String getOutputEncoding() {
        return "UTF-8";
    }

    @Override
    public boolean hasGeneratedOutput() {
        return true;
    }
    
}
