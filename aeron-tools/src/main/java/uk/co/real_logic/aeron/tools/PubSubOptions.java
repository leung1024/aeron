package uk.co.real_logic.aeron.tools;

import org.apache.commons.cli.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by bhorst on 3/3/15.
 */
public class PubSubOptions
{
    final Options options;

    boolean useEmbeddedDriver;
    long randomSeed;
    long messages;
    long threads;
    long iterations;
    final List<ChannelDescriptor> channels;
    MessageSizePattern sizePattern;

    public PubSubOptions()
    {
        // TODO: Add more detail to the descriptions
        options = new Options();
        options.addOption("c",  "channels",   true, "Create the given channels.");
        options.addOption("d",  "data",       true, "Send data file or verifiable stream.");
        options.addOption(null, "driver",     true, " Use 'external' or 'embedded' Aeron driver.");
        options.addOption("i",  "iterations", true, "Run the rate sequence n times.");
        options.addOption("m",  "messages",   true, "Send n messages before exiting.");
        options.addOption("r",  "rate",       true, "Send rate pattern.");
        options.addOption(null, "seed",       true, "Random number generator seed.");
        options.addOption(null, "session",    true, "Use session id for all publishers.");
        options.addOption("s",  "size",       true, "Message payload size sequence, in bytes.");
        options.addOption("t",  "threads",    true, "Number of threads.");

        // these will all be overridden in parseArgs
        randomSeed = 0;
        threads = 0;
        messages = 0;
        iterations = 0;
        useEmbeddedDriver = false;
        sizePattern = null;
        channels = new ArrayList<ChannelDescriptor>();
    }

    /**
     * Parse command line arguments into usable objects.
     * @param args
     * @throws ParseException
     */
    public void parseArgs(String[] args) throws ParseException
    {
        CommandLineParser parser = new GnuParser();
        CommandLine command = parser.parse(options, args);

        String opt;

        // threads, default = 1
        opt = command.getOptionValue("t", "1");
        try
        {
            setThreads(Long.parseLong(opt));
        }
        catch (NumberFormatException threadsEx)
        {
            throw new ParseException("Couldn't parse threads value '" + opt + "' as type long.");
        }

        // seed, default = 0
        opt = command.getOptionValue("seed", "0");
        try
        {
            randomSeed = Long.parseLong(opt);
        }
        catch (NumberFormatException seedEx)
        {
            throw new ParseException("Couldn't parse randomSeed value '" + opt + "' as type long.");
        }

        // messages, default = unlimited (max long value)
        opt = command.getOptionValue("messages", "unlimited");
        try
        {
            if (opt.equalsIgnoreCase("unlimited"))
            {
                messages = Long.MAX_VALUE;
            }
            else
            {
                messages = Long.parseLong(opt);
            }
        }
        catch (NumberFormatException messagesEx)
        {
            throw new ParseException("Couldn't parse messages value '" + opt + "' as type long.");
        }

        // iterations, default = 1
        opt = command.getOptionValue("iterations", "1");
        try
        {
            iterations = Long.parseLong(opt);
        }
        catch (NumberFormatException iterationsEx)
        {
            throw new ParseException("Couldn't parse iterations value '" + opt + "' as type long.");
        }

        // driver, default = external
        opt = command.getOptionValue("driver", "external");
        if (opt.equalsIgnoreCase("external"))
        {
            useEmbeddedDriver = false;
        }
        else if (opt.equalsIgnoreCase("embedded"))
        {
            useEmbeddedDriver = true;
        }

        // channels, default = udp://localhost:31111
        opt = command.getOptionValue("channels", "udp://localhost:31111#1");
        parseChannels(opt);

        opt = command.getOptionValue("size", "32");
        parseMessageSizes(opt);
    }

    public List<ChannelDescriptor> getChannels()
    {
        return channels;
    }

    /**
     * Print the help message for the available options.
     * @param program Name of the program calling print help.
     */
    public void printHelp(String program)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(program, options);
    }

    /**
     * Get the number of threads for the application to use.
     * @return
     */
    public long getThreads()
    {
        return threads;
    }

    /**
     * Set the number of threads for the application to use.
     * @param t Number of threads.
     */
    public void setThreads(long t)
    {
        threads = t;
    }

    public boolean getUseEmbeddedDriver()
    {
        return useEmbeddedDriver;
    }

    public MessageSizePattern getMessageSizePattern()
    {
        return this.sizePattern;
    }
    /**
     * Parses a comma separated list of channels. The channels can use ranges for ports and
     * stream-id on a per address basis. Channel Example: udp://192.168.0.100:21000-21004#1-10
     * will give 5 channels with 10 streams each.
     * @param csv
     * @throws ParseException
     */
    private void parseChannels(String csv) throws ParseException
    {
        String channel;
        int portLow = 0;
        int portHigh = 0;
        int streamIdLow = 1;
        int streamIdHigh = 1;

        String[] channelDescriptions = csv.split(",");
        for (int i = 0; i < channelDescriptions.length; i++)
        {
            // channelComponents should have 1 or 2 pieces
            // 1 when only an address is supplied, 2 when an address and stream-id are supplied.
            String[] channelComponents = channelDescriptions[i].split("#");
            if (channelComponents.length > 2)
            {
                throw new ParseException("Channel '" + channelDescriptions[i] + "' has too many '#' characters");
            }

            // address has 2 parts udp://<addr>:<port(s)>
            String address = channelComponents[0];
            String[] addressComponents = address.split(":");
            if (addressComponents.length != 3)
            {
                throw new ParseException("Channel address '" + address + "' has too many ':' characters.");
            }
            channel = addressComponents[0] + ":" + addressComponents[1];

            // get the port, or port range
            String ports = addressComponents[2];
            int[] portsArray = findMinAndMaxPort(ports);
            portLow = portsArray[0];
            portHigh = portsArray[1];

            // get stream Ids
            if (channelComponents.length > 1)
            {
                String ids = channelComponents[1];
                int[] streamIdRange = findMinAndMaxStreamIds(ids);
                streamIdLow = streamIdRange[0];
                streamIdHigh = streamIdRange[1];
            }
            else
            {
                // no stream id specified, just use 1 for low and high
                streamIdLow = 1;
                streamIdHigh = 1;
            }

            // Sanity Check ports and streams
            if (portLow < 0 || portLow > 65535)
            {
                throw new ParseException("Low port of '" + channelDescriptions[i] + "' is not a valid port.");
            }
            if (portHigh < 0 || portHigh > 65535)
            {
                throw new ParseException("High port of '" + channelDescriptions[i] + "' is not a valid port.");
            }
            if (portLow > portHigh)
            {
                throw new ParseException("Low port of '" + channelDescriptions[i] + "' is greater than high port.");
            }
            if (streamIdLow > streamIdHigh)
            {
                throw new ParseException("Low stream-id of '" + channelDescriptions[i] + "' is greater than high stream-id.");
            }

            // OK, now create the channels.
            addChannelRanges(channel, portLow, portHigh, streamIdLow, streamIdHigh);
        }
    }

    /**
     * Helper function to find low and high port from the port string in an address. This is mostly here
     * so that the parseChannels method isn't huge.
     * @param ports The port string which is either a number or range containing a hyphen.
     * @return An array of length 2 containing the low and high.
     */
    private int[] findMinAndMaxPort(String ports) throws ParseException
    {
        int portLow = 0;
        int portHigh = 0;
        if (ports.contains("-"))
        {
            // It's a range in the form portLow-portHigh
            String[] portRangeStrings = ports.split("-");
            if (portRangeStrings.length != 2)
            {
                throw new ParseException("Address port range '" + ports + "' contains too many '-' characters.");
            }

            try
            {
                portLow = Integer.parseInt(portRangeStrings[0]);
                portHigh = Integer.parseInt(portRangeStrings[1]);
            }
            catch (NumberFormatException portRangeEx)
            {
                throw new ParseException("Address port range '" + ports + "' did not parse into two integers.");
            }
        }
        else
        {
            // It's a single port
            try
            {
                portLow = Integer.parseInt(ports);
                portHigh = portLow;
            }
            catch (NumberFormatException portEx)
            {
                throw new ParseException("Address port '" + ports + "' didn't parse into an integer");
            }
        }
        if (portLow > portHigh)
        {
            throw new ParseException("Address port range '" + ports + "' has low port greater than high port.");
        }
        return new int[] { portLow, portHigh };
    }

    /**
     * Helper function to find the minimum and maximum values in the stream ID section of a channel.
     * This is mostly here so the parse channels function isn't too large.
     * @param ids String containing the ids, either single integer or 2 integer range with hyphen.
     * @return An array that is always length 2 which contains minimum and maximum stream IDs.
     */
    private int[] findMinAndMaxStreamIds(String ids) throws ParseException
    {
        int streamIdLow = 1;
        int streamIdHigh = 1;

        if (ids.contains("-"))
        {
            // identifier strings contain a low and a high
            String[] idRange = ids.split("-");
            if (idRange.length != 2)
            {
                throw new ParseException("Stream ID range '" + ids + "' has too many '-' characters.");
            }
            try
            {
                streamIdLow = Integer.parseInt(idRange[0]);
                streamIdHigh = Integer.parseInt(idRange[1]);
            }
            catch (NumberFormatException idRangEx)
            {
                throw new ParseException("Stream ID range '" + ids + "' did not parse into two integers.");
            }
        }
        else
        {
            // single Id specified
            try
            {
                streamIdLow = Integer.parseInt(ids);
                streamIdHigh = streamIdLow;
            }
            catch (NumberFormatException streamIdEx)
            {
                throw new ParseException("Stream ID '" + ids + "' did not parse into an int.");
            }
        }

        return new int[] { streamIdLow, streamIdHigh };
    }

    /**
     * Function to add ChannelDescriptor objects to the channels list.
     * @param baseAddress Channel address without :port
     * @param portLow
     * @param portHigh
     * @param streamIdLow
     * @param streamIdHigh
     */
    private void addChannelRanges(String baseAddress, int portLow, int portHigh, int streamIdLow, int streamIdHigh)
    {
        int currentPort = portLow;
        while (currentPort <= portHigh)
        {
            ChannelDescriptor cd = new ChannelDescriptor();
            cd.setChannel(baseAddress + ":" + currentPort);

            int[] idArray = new int[streamIdHigh - streamIdLow + 1];
            int currentStream = streamIdLow;
            for (int i = 0; i < idArray.length; i++)
            {
                // set all the Ids in the array
                idArray[i] = currentStream++;
            }
            cd.setStreamIdentifiers(idArray);
            channels.add(cd);
            currentPort++;
        }
    }

    private void parseMessageSizes(String cvs) throws ParseException
    {
        long numMessages = 0;
        int messageSizeMin = 0;
        int messageSizeMax = 0;

        String[] sizeEntries = cvs.split(",");
        for (int i = 0; i < sizeEntries.length; i++)
        {
            // The message size may be separated with a '@' to send a number of messages at a given size or range.
            String entryStr = sizeEntries[i];
            String[] entryComponents = entryStr.split("@");
            if (entryComponents.length > 2)
            {
                throw new ParseException("Message size '" + entryStr + "' contains too many '@' characters.");
            }

            String sizeStr;
            // Get number of messages and find the size string to be parsed later
            if (entryComponents.length == 2)
            {
                // contains a number of messages followed by size or size range.
                // Example: 100@8K-1MB (100 messages between 8 kilobytes and 1 megabyte in length)
                try
                {
                    numMessages = Long.parseLong(entryComponents[0]);
                }
                catch (NumberFormatException numMessagesEx)
                {
                    throw new ParseException("Number of messages in '" + entryStr +"' could not parse as long value");
                }
                sizeStr = entryComponents[1];
            }
            else
            {
                numMessages = Long.MAX_VALUE;
                sizeStr = entryComponents[0];
            }

            // parse the size string
            String[] sizeRange = sizeStr.split("-");
            if (sizeRange.length > 2)
            {
                throw new ParseException("Message size range in '" + entryStr + "' has too many '-' characters.");
            }

            messageSizeMin = parseSize(sizeRange[0]);
            messageSizeMax = messageSizeMin;
            if (sizeRange.length == 2)
            {
                // A range was specified, find the max value
                messageSizeMax = parseSize(sizeRange[1]);
            }
            addSizeRange(numMessages, messageSizeMin, messageSizeMax);
        } // end for loop
    }

    /**
     * Parse a size into bytes. The size is a number with or without a suffix. The total bytes must be less
     * than Integer.MAX_VALUE.
     * Possible suffixes: B,b for bytes
     *                    KB,kb,K,k for kilobyte (1024 bytes)
     *                    MB,mb,M,m for megabytes (1024*1024 bytes)
     * @param sizeStr String containing formatted size
     * @return Number of bytes
     * @throws ParseException When input is invalid or number of bytes too large.
     */
    private int parseSize(String sizeStr) throws ParseException
    {
        final int kb = 1024;
        final int mb = 1024*1024;
        int multiplier = 1;
        long size = 0;
        final String numberStr;

        if (sizeStr.endsWith("KB") || sizeStr.contains("kb"))
        {
            multiplier = kb;
            numberStr = sizeStr.substring(0, sizeStr.length() - 2);
        }
        else if (sizeStr.endsWith("K") || sizeStr.endsWith("k"))
        {
            multiplier = kb;
            numberStr = sizeStr.substring(0, sizeStr.length() - 1);
        }
        else if (sizeStr.endsWith("MB") || sizeStr.contains("mb"))
        {
            multiplier = mb;
            numberStr = sizeStr.substring(0, sizeStr.length() - 2);
        }
        else if (sizeStr.endsWith("M") || sizeStr.endsWith("m"))
        {
            multiplier = mb;
            numberStr = sizeStr.substring(0, sizeStr.length() - 1);
        }
        else if (sizeStr.endsWith("B") || sizeStr.endsWith("b"))
        {
            multiplier = 1;
            numberStr = sizeStr.substring(0, sizeStr.length() - 1);
        }
        else
        {
            // No suffix, assume bytes.
            multiplier = 1;
            numberStr = sizeStr;
        }

        try
        {
            size = Long.parseLong(numberStr);
        }
        catch (Exception ex)
        {
            throw new ParseException("Could not parse '" + numberStr + "' into a long value.");
        }
        size *= multiplier;

        if (size > Integer.MAX_VALUE || size < 0)
        {
            // can't be larger than max signed int (2 gb) or less than 0.
            throw new ParseException("Payload size '" + sizeStr + "' too large or negative.");
        }
        return (int)size;
    }


    private void addSizeRange(long messages, int minSize, int maxSize) throws ParseException
    {
        try
        {
            if (sizePattern == null)
            {
                sizePattern = new MessageSizePattern(messages, minSize, maxSize);
            }
            else
            {
                sizePattern.addPatternEntry(messages, minSize, maxSize);
            }
        }
        catch (Exception ex)
        {
            throw new ParseException(ex.getMessage());
        }
    }
}
