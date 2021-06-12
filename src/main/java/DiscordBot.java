import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberUpdateEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.Channel;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class initiates and sets events for the discord bot.
 */

public class DiscordBot {
    //Default values, prefix & similarity index, values are updated on command.
    private String prefix = "~";
    private double similarity = 13;
    private int divisor = 2;
    private String currentAvatarType = "Undefined";
    private Double currentIndex = 0.0;

    //Lists of places to notify:
    private ArrayList<Mono<Channel>> channelsToNotify;
    private ArrayList<Mono<User>> usersToNotify;
    private ArrayList<String> rolesToNotify;
    private ArrayList<String> ignoreList;

    private final ArrayList<RoleObject> roles = new ArrayList<>();

    /**
     * Updates similarity index at boot up and at command similarity set.
     */
    private void updateProperties(){
        MySQLAccess simDB = new MySQLAccess();
        String simQuery = "SELECT * FROM properties WHERE id = 1";
        String prefixQuery = "SELECT * FROM properties WHERE id = 2";
        String divisorQuery = "SELECT * FROM properties WHERE id = 3";

        ResultSet simResultSet = null;
        ResultSet prefixResultSet = null;
        ResultSet divisorResultSet = null;

        try{
            Statement statement = simDB.connect().createStatement();
            Statement statement1 = simDB.connect().createStatement();
            Statement statement2 = simDB.connect().createStatement();
                    
            simResultSet = statement.executeQuery(simQuery);
            prefixResultSet = statement1.executeQuery(prefixQuery);
            divisorResultSet = statement2.executeQuery(divisorQuery);

            //Default value if error
            String similarityResult = "" + 13;
            String prefixResult = "~";
            String divisorResult = "2";

            while(simResultSet.next()){
                similarityResult = simResultSet.getString(2);
            }
            while(prefixResultSet.next()){
                prefixResult = prefixResultSet.getString(2);
            }
            while(divisorResultSet.next()){
                divisorResult = divisorResultSet.getString(2);
            }

            try{
                if (Double.parseDouble(similarityResult) != similarity){
                    similarity = Double.parseDouble(similarityResult);
                }
            } catch (Exception e){
                System.out.println("Similarity error default maintained: " + e);
            }

            try{
                if (!prefix.equals(prefixResult)){
                    prefix = prefixResult;
                }
            } catch (Exception e){
                System.out.println("Prefix error default maintained: " + e);
            }

            try{
                if (Integer.parseInt(divisorResult) != divisor){
                    divisor = Integer.parseInt(divisorResult);
                }
            } catch (Exception e){
                System.out.println("Divisor error default maintained: " + e);
            }

        } catch (SQLException throwable) {
            throwable.printStackTrace();
        } finally {
            simDB.disconnect();
            try {
                if (simResultSet != null) {
                    simResultSet.close();
                }
                if (prefixResultSet != null){
                    prefixResultSet.close();
                }
                if (divisorResultSet != null){
                    divisorResultSet.close();
                }
            }catch (SQLException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Updates the notification list.
     * @param client gateway client for discord bot.
     */
    private void updateNotifyList(GatewayDiscordClient client){
        channelsToNotify = new ArrayList<>();
        usersToNotify = new ArrayList<>();
        rolesToNotify = new ArrayList<>();

        MySQLAccess notifyDB = new MySQLAccess();
        String notifyQuery = "SELECT * FROM notify";
        ResultSet notifyResultSet = null;

        try{
            Statement statement = notifyDB.connect().createStatement();
            notifyResultSet = statement.executeQuery(notifyQuery);

            while(notifyResultSet.next()){
                String id = notifyResultSet.getString(1);
                int type = notifyResultSet.getInt(2);

                if (type == 0){
                    channelsToNotify.add(client.getChannelById(Snowflake.of(id)));
                }else if(type == 1){
                    usersToNotify.add(client.getUserById(Snowflake.of(id)));
                }else if(type == 2){
                    String roleNotifySB = "<@&" +
                            id +
                            ">";
                    rolesToNotify.add(roleNotifySB);
                }
            }
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        } finally {
            notifyDB.disconnect();
            try {
                if (notifyResultSet != null) {
                    notifyResultSet.close();
                }
            }catch (SQLException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Updates local roles, at start of bot connection & at roles related commands.
     */
    private void updateAllowedRoles(){
        roles.clear();

        Set<Snowflake> rolesAvatar = new HashSet<>(); //ID: 1

        Set<Snowflake> rolesBlackListAdd = new HashSet<>(); //ID: 2
        Set<Snowflake> rolesBlackListRemove = new HashSet<>(); //ID: 3
        Set<Snowflake> rolesBlackListSearch = new HashSet<>(); //ID: 4

        Set<Snowflake> rolesCommands = new HashSet<>(); //ID: 5
        Set<Snowflake> rolesCommandsInfo = new HashSet<>(); //ID: 6

        Set<Snowflake> rolesIgnoreAdd = new HashSet<>(); //ID: 7
        Set<Snowflake> rolesIgnoreRemove = new HashSet<>(); //ID: 8
        Set<Snowflake> rolesIgnoreSearch = new HashSet<>(); //ID: 9

        Set<Snowflake> rolesNotifyAdd = new HashSet<>(); //ID: 10
        Set<Snowflake> rolesNotifyRemove = new HashSet<>(); //ID: 11
        Set<Snowflake> rolesNotifySearch = new HashSet<>(); //ID: 12

        Set<Snowflake> rolesPermissionsAdd = new HashSet<>(); //ID: 13
        Set<Snowflake> rolesPermissionsRemove = new HashSet<>(); //ID: 14
        Set<Snowflake> rolesPermissions = new HashSet<>(); //ID: 15

        Set<Snowflake> rolesPrefixSet = new HashSet<>(); //ID: 16

        Set<Snowflake> rolesScan = new HashSet<>(); //ID: 17

        Set<Snowflake> rolesSimilaritySet = new HashSet<>(); //ID: 18
        Set<Snowflake> rolesSimilaritySearch = new HashSet<>(); //ID: 19


        MySQLAccess rolesDBB = new MySQLAccess();
        String rolesQuery = "SELECT * FROM roles";
        ResultSet rolesResultSet = null;

        boolean hAvatar = false; //ID: 1

        boolean hBlackListAdd = false; //ID: 2
        boolean hBlackListRemove = false; //ID: 3
        boolean hBlackListSearch = false; //ID: 4

        boolean hCommands  = false; //ID: 5
        boolean hCommandsInfo  = false; //ID: 6

        boolean hIgnoreAdd = false; //ID: 7
        boolean hIgnoreRemove = false; //ID: 8
        boolean hIgnoreSearch = false; //ID: 9

        boolean hNotifyAdd = false; //ID: 6
        boolean hNotifyRemove = false; //ID: 7
        boolean hNotifySearch = false; //ID: 8

        boolean hPermissionsAdd = false; //ID: 9
        boolean hPermissionsRemove = false; //ID: 10
        boolean hPermissions = false; //ID: 11
        
        boolean hPrefixSet = false; //ID: 12

        boolean hScan = false; //ID: 13

        boolean hSimilaritySet = false; //ID: 14
        boolean hSimilaritySearch = false; //ID: 15
        
        
        try{
            Statement statement = rolesDBB.connect().createStatement();
            rolesResultSet = statement.executeQuery(rolesQuery);

            while(rolesResultSet.next()){
                String role = rolesResultSet.getString(1);
                int commandId = rolesResultSet.getInt(2);
                int higherRoleAllowed = rolesResultSet.getInt(3);

                switch (commandId){
                    case 1:
                        rolesAvatar.add(Snowflake.of(role));
                        hAvatar = higherRoleAllowed != 0;
                        break;
                    case 2:
                        rolesBlackListAdd.add(Snowflake.of(role));
                        hBlackListAdd = higherRoleAllowed != 0;
                        break;
                    case 3:
                        rolesBlackListRemove.add(Snowflake.of(role));
                        hBlackListRemove = higherRoleAllowed != 0;
                        break;
                    case 4:
                        rolesBlackListSearch.add(Snowflake.of(role));
                        hBlackListSearch = higherRoleAllowed != 0;
                        break;
                    case 5:
                        rolesCommands.add(Snowflake.of(role));
                        hCommands = higherRoleAllowed != 0;
                        break;
                    case 6:
                        rolesCommandsInfo.add(Snowflake.of(role));
                        hCommandsInfo = higherRoleAllowed != 0;
                        break;
                    case 7:
                        rolesIgnoreAdd.add(Snowflake.of(role));
                        hIgnoreAdd = higherRoleAllowed != 0;
                        break;
                    case 8:
                        rolesIgnoreRemove.add(Snowflake.of(role));
                        hIgnoreRemove = higherRoleAllowed != 0;
                        break;
                    case 9:
                        rolesIgnoreSearch.add(Snowflake.of(role));
                        hIgnoreSearch = higherRoleAllowed != 0;
                        break;
                    case 10:
                        rolesNotifyAdd.add(Snowflake.of(role));
                        hNotifyAdd = higherRoleAllowed != 0;
                        break;
                    case 11:
                        rolesNotifyRemove.add(Snowflake.of(role));
                        hNotifyRemove = higherRoleAllowed != 0;
                        break;
                    case 12:
                        rolesNotifySearch.add(Snowflake.of(role));
                        hNotifySearch = higherRoleAllowed != 0;
                        break;
                    case 13:
                        rolesPermissionsAdd.add(Snowflake.of(role));
                        hPermissionsAdd = higherRoleAllowed != 0;
                        break;
                    case 14:
                        rolesPermissionsRemove.add(Snowflake.of(role));
                        hPermissionsRemove = higherRoleAllowed != 0;
                        break;
                    case 15:
                        rolesPermissions.add(Snowflake.of(role));
                        hPermissions = higherRoleAllowed != 0;
                        break;
                    case 16:
                        rolesPrefixSet.add(Snowflake.of(role));
                        hPrefixSet = higherRoleAllowed != 0;
                        break;
                    case 17:
                        rolesScan.add(Snowflake.of(role));
                        hScan = higherRoleAllowed != 0;
                        break;
                    case 18:
                        rolesSimilaritySet.add(Snowflake.of(role));
                        hSimilaritySet = higherRoleAllowed != 0;
                        break;
                    case 19:
                        rolesSimilaritySearch.add(Snowflake.of(role));
                        hSimilaritySearch = higherRoleAllowed != 0;
                        break;
                }
            }
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        } finally {
            rolesDBB.disconnect();
            try {
                if (rolesResultSet != null) {
                    rolesResultSet.close();
                }
            }catch (SQLException e){
                e.printStackTrace();
            }
            try{
                if (!rolesAvatar.isEmpty()){
                    roles.add(new RoleObject(rolesAvatar,1,hAvatar));
                }
                if (!rolesBlackListAdd.isEmpty()){
                    roles.add(new RoleObject(rolesBlackListAdd,2,hBlackListAdd));
                }
                if (!rolesBlackListRemove.isEmpty()){
                    roles.add(new RoleObject(rolesBlackListRemove,3,hBlackListRemove));
                }
                if (!rolesBlackListSearch.isEmpty()) {
                    roles.add(new RoleObject(rolesBlackListSearch, 4, hBlackListSearch));
                }
                if (!rolesCommands.isEmpty()) {
                    roles.add(new RoleObject(rolesCommands, 5, hCommands));
                }
                if (!rolesCommandsInfo.isEmpty()) {
                    roles.add(new RoleObject(rolesCommandsInfo, 6, hCommandsInfo));
                }
                if (!rolesIgnoreAdd.isEmpty()) {
                    roles.add(new RoleObject(rolesIgnoreAdd, 7, hIgnoreAdd));
                }
                if (!rolesIgnoreRemove.isEmpty()) {
                    roles.add(new RoleObject(rolesIgnoreRemove, 8, hIgnoreRemove));
                }
                if (!rolesIgnoreSearch.isEmpty()) {
                    roles.add(new RoleObject(rolesIgnoreSearch, 9, hIgnoreSearch));
                }
                if (!rolesNotifyAdd.isEmpty()) {
                    roles.add(new RoleObject(rolesNotifyAdd, 10, hNotifyAdd));
                }
                if (!rolesNotifyRemove.isEmpty()) {
                    roles.add(new RoleObject(rolesNotifyRemove, 11, hNotifyRemove));
                }
                if (!rolesNotifySearch.isEmpty()) {
                    roles.add(new RoleObject(rolesNotifySearch, 12, hNotifySearch));
                }
                if (!rolesPermissionsAdd.isEmpty()) {
                    roles.add(new RoleObject(rolesPermissionsAdd, 13, hPermissionsAdd));
                }
                if (!rolesPermissionsRemove.isEmpty()) {
                    roles.add(new RoleObject(rolesPermissionsRemove, 14, hPermissionsRemove));
                }
                if (!rolesPermissions.isEmpty()){
                    roles.add(new RoleObject(rolesPermissions,15,hPermissions));
                }
                if (!rolesPrefixSet.isEmpty()){
                    roles.add(new RoleObject(rolesPrefixSet,16,hPrefixSet));
                }
                if (!rolesScan.isEmpty()) {
                    roles.add(new RoleObject(rolesScan, 17, hScan));
                }
                if (!rolesSimilaritySet.isEmpty()){
                    roles.add(new RoleObject(rolesSimilaritySet,18,hSimilaritySet));
                }
                if (!rolesSimilaritySearch.isEmpty()){
                    roles.add(new RoleObject(rolesSimilaritySearch,19,hSimilaritySearch));
                }

            } catch (Exception e){
                System.out.println("Error while adding roles: " + e);
            }
        }
    }

    /**
     * Updates ignore list, start of bot connection at related command execution.
     */
    private void updateIgnoreList(){
        ignoreList = new ArrayList<>();

        MySQLAccess ignoreListDB = new MySQLAccess();
        String notifyQuery = "SELECT * FROM ignore_list";
        ResultSet ignoreResultSet = null;

        try{
            Statement statement = ignoreListDB.connect().createStatement();
            ignoreResultSet = statement.executeQuery(notifyQuery);

            while(ignoreResultSet.next()){
                String id = ignoreResultSet.getString(1);
                ignoreList.add(id);
            }
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        } finally {
            ignoreListDB.disconnect();
            try {
                if (ignoreResultSet != null) {
                    ignoreResultSet.close();
                }
            }catch (SQLException e){
                e.printStackTrace();
            }
        }

    }

    /**
     * A method to find a match within an AvatarDiffReceipt list.
     * @param  avatarDiffList list of avatar differences alongside their identifiers.
     * @return bool if match otherwise false.
     */
    private boolean findMatch(List<AvatarDiffReceipt> avatarDiffList, double similarity) {
        for (AvatarDiffReceipt aDiff : avatarDiffList) {
            if (aDiff.getDifferenceIndex() < similarity) {
                currentAvatarType = aDiff.getType();
                currentIndex = aDiff.getDifferenceIndex();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns List of the List argument passed to this function with size = chunkSize
     *
     * @param <T> Generic type of the List
     * @return A list of Lists which is portioned from the original list
     */
    public static <T> List<List<T>> chunkList(List<T> collection, int batchSize){
        return IntStream.iterate(0, i -> i < collection.size(), i -> i + batchSize)
                .mapToObj(i -> collection.subList(i, Math.min(i + batchSize, collection.size())))
                .collect(Collectors.toList());
    }

    public static <T> List<List<T>> chunk(List<T> input, int chunkSize) {
        int inputSize = input.size();
        int chunkCount = (int) Math.ceil(inputSize / (double) chunkSize);

        Map<Integer, List<T>> map = new HashMap<>(chunkCount);
        List<List<T>> chunks = new ArrayList<>(chunkCount);

        for (int i = 0; i < inputSize; i++){
            map.computeIfAbsent(i / chunkSize, (ignore) -> {
                List<T> chunk = new ArrayList<>();
                chunks.add(chunk);
                return chunk;
            }).add(input.get(i));
        }

        return chunks;
    }


    /**
     * A method to find if a URL is valid or not.
     * @param url link to be checked.
     * @return boolean corresponding to validity of link.
     */
    private boolean isURLValid(String url){
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Method to decide if string contains only digits.
     * @param arg string to check.
     * @return boolean depending whether arg contains only digits.
     */
    private boolean onlyDigits(String arg) {
        String regex = "[0-9]+";
        Pattern pattern = Pattern.compile(regex);

        if (arg == null) {
            return false;
        }

        Matcher matcher = pattern.matcher(arg);
        return matcher.matches();
    }

    /**
     * Checks if a provided user has the right permissions.
     * @param commandID The particular command to check permissions.
     * @param userRoles The users roles.
     * @param user The user.
     * @param guildOwnerID The guild OwnerID, if owner naturally it overrides all permissions.
     * @return A boolean to determine if they have the proper roles.
     */
    private boolean isAllowed(int commandID, List<Role> userRoles, Mono<Member> user, Snowflake guildOwnerID){
        Set<Snowflake> allowedRoles = null;
        boolean allowHigher = false;

        //Populate allowedRoles
        for (RoleObject role : roles) {
            if (role.getCommandID() == commandID) {
                allowedRoles = role.getRoleIDs();
                if (role.isHigherRoleAllowed()){
                  allowHigher = true;
                }
            }
        }

        Set<Snowflake> finalAllowedRoles = allowedRoles;
        AtomicBoolean isAllowed = new AtomicBoolean(false);

        if (allowHigher){
            Mono<Boolean> isAllowedMono = user.map(member -> member.hasHigherRoles(finalAllowedRoles))
                    .onErrorReturn(Mono.just(false))
                    .defaultIfEmpty(Mono.just(false))
                    .block();

            if (isAllowedMono != null) {
                isAllowedMono.subscribe(
                        isAllowed::set,
                        error -> System.out.println("Error occurred or false")
                );
            }
        }

        Snowflake userID = user.map(User::getId).block();

        if (userRoles != null){
            if (userRoles.isEmpty()){
                //If owner and permissions are empty.
                if (userID != null && userID.equals(guildOwnerID)) {
                    return true;
                }
            }

            //If user Id is in permissions
            if (allowedRoles != null){
                if (allowedRoles.contains(userID)){
                    return true;
                }
            }

            return userRoles.stream()
                    .anyMatch(role -> {
                        if (role.getPermissions().contains(Permission.ADMINISTRATOR)
                                || Objects.equals(userID, guildOwnerID)
                                || isAllowed.get()) {
                            return true;
                        }

                        if (finalAllowedRoles == null) {
                            return false;
                        }

                        return finalAllowedRoles.contains(role.getId());
                    });
        }else{
            return false;
        }
    }

    /**
     * Creates and initializes gateway client, events and corresponding commands.
     */
    public void startBot(){
        PropertyValues propertyValues = new PropertyValues();

        try {
            propertyValues.initPropertyValues();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        MySQLAccess database = new MySQLAccess();

        GatewayDiscordClient client = DiscordClient.create(propertyValues.getToken())
                .gateway()
                .setEnabledIntents(IntentSet.of(
                        Intent.GUILDS,
                        Intent.GUILD_MEMBERS,
                        Intent.GUILD_MESSAGES,
                        Intent.GUILD_PRESENCES
                ))
                .login()
                .block();

        assert client != null;

        //Initializations:
        updateNotifyList(client);
        updateAllowedRoles();
        updateProperties();
        updateIgnoreList();

        ImageRecognition imageRecognition = new ImageRecognition();
        imageRecognition.populateBlacklist();

        ArrayList<String> notifiedAvatars = new ArrayList<>();


        //Events:

        //Member update event (role change, avatar change etc)
        client.getEventDispatcher().on(MemberUpdateEvent.class)
                .map(MemberUpdateEvent::getMember)
                .filter(memberMono -> memberMono.map(member -> !member.isBot()).blockOptional().orElse(false))
                .flatMap(memberMono -> memberMono.map(member -> {
                    String memberAvatar = member.getAvatarUrl();
                    String memberID = member.getId().asString();
                    try {
                        if (!(ignoreList.contains(memberID))){
                            List<AvatarDiffReceipt> avatarDiff = new ArrayList<>(imageRecognition.pixelSimilarity(memberAvatar));
                            if(findMatch(avatarDiff,similarity)){
                                if (rolesToNotify.isEmpty() || notifiedAvatars.contains(memberAvatar)){
                                    for (Mono<Channel> channel : channelsToNotify) {
                                        channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                "Found Possible Match: <@"
                                                        + member.getId().asLong()
                                                        + "> (ID: " + member.getId().asLong() + ")"
                                                        + " for: `" + currentAvatarType + "`"
                                                        + " Similarity Index: `" + currentIndex + "`")).block();
                                    }
                                }else{
                                    StringBuilder notifyRolesSB = new StringBuilder();
                                    for (int i = 0; i < rolesToNotify.size(); i++){
                                        if (i == rolesToNotify.size() - 1){
                                            notifyRolesSB.append(rolesToNotify.get(i));
                                        }else{
                                            notifyRolesSB.append(rolesToNotify.get(i)).append(",");
                                        }
                                    }
                                    for (Mono<Channel> channel : channelsToNotify) {
                                        channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                notifyRolesSB.toString() + "Found Possible Match: <@"
                                                        + member.getId().asLong()
                                                        + "> (ID: " + member.getId().asLong() + ")"
                                                        + " for: `" + currentAvatarType + "`"
                                                        + " Similarity Index: `" + currentIndex + "`")).block();
                                    }
                                }

                                for (Mono<User> userMono : usersToNotify) {
                                    try{
                                        Objects.requireNonNull(Objects.requireNonNull(userMono.block()).getPrivateChannel().block()).createMessage(
                                                "Found Possible Match: <@"
                                                        + member.getId().asLong()
                                                        + "> (ID: " + member.getId().asLong() + ")"
                                                        + " for: `" + currentAvatarType + "`").block();
                                    } catch (ClientException e){
                                        System.out.println("Client error: " + e);
                                    }
                                }

                                notifiedAvatars.add(memberAvatar);

                                System.out.println("Found Possible Match: "
                                        + member.getDisplayName()
                                        + "#" + member.getDiscriminator()
                                        + " (" + member.getId().asLong() + ")"
                                        +  "[" + currentAvatarType + "]"
                                        + "|" + currentIndex + "|");
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return "\nAvatar Checked: "
                            + member.getDisplayName() + "#"
                            + member.getDiscriminator()
                            + " (" + member.getId().asLong() + ")"
                            + "\nAvatar URL: "
                            + memberAvatar;
                }).onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                }))
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .subscribe(System.out::println);

        //Member join event
        client.getEventDispatcher().on(MemberJoinEvent.class)
                .map(MemberJoinEvent::getMember)
                .filter(member -> !member.isBot())
                .map(member -> {
                    String memberAvatar = member.getAvatarUrl();
                    String memberID = member.getId().asString();

                    try {
                        if (!ignoreList.contains(memberID)){
                            List<AvatarDiffReceipt> avatarDiff = new ArrayList<>(imageRecognition.pixelSimilarity(memberAvatar));
                            if(findMatch(avatarDiff,similarity)){
                                if (rolesToNotify.isEmpty() || notifiedAvatars.contains(memberAvatar)){
                                    for (Mono<Channel> channel : channelsToNotify) {
                                        channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                "Found Possible Match: <@"
                                                        + member.getId().asLong()
                                                        + "> (ID: " + member.getId().asLong() + ")"
                                                        + " for: `" + currentAvatarType + "`"
                                                        + " Similarity Index: `" + currentIndex + "`")).block();
                                    }
                                }else{
                                    StringBuilder notifyRolesSB = new StringBuilder();
                                    for (int i = 0; i < rolesToNotify.size(); i++){
                                        if (i == rolesToNotify.size() - 1){
                                            notifyRolesSB.append(rolesToNotify.get(i));
                                        }else{
                                            notifyRolesSB.append(rolesToNotify.get(i)).append(",");
                                        }
                                    }
                                    for (Mono<Channel> channel : channelsToNotify) {
                                        channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                notifyRolesSB.toString() + "Found Possible Match: <@"
                                                        + member.getId().asLong()
                                                        + "> (ID: " + member.getId().asLong() + ")"
                                                        + " for: `" + currentAvatarType + "`"
                                                        + " Similarity Index: `" + currentIndex + "`")).block();
                                    }
                                }

                                for (Mono<User> userMono : usersToNotify) {
                                    try{
                                        Objects.requireNonNull(Objects.requireNonNull(userMono.block()).getPrivateChannel().block()).createMessage(
                                                "Found Possible Match: <@"
                                                        + member.getId().asLong()
                                                        + "> (ID: " + member.getId().asLong() + ")"
                                                        + " for: `" + currentAvatarType + "`").block();
                                    } catch (ClientException e){
                                        System.out.println("Client error: " + e);
                                    }
                                }

                                notifiedAvatars.add(memberAvatar);

                                System.out.println("Found Possible Match: "
                                        + member.getDisplayName()
                                        + "#" + member.getDiscriminator()
                                        + " (" + member.getId().asLong() + ")"
                                        +  "[" + currentAvatarType + "]"
                                        + "|" + currentIndex + "|");
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return "\nAvatar Checked: "
                            + member.getDisplayName() + "#"
                            + member.getDiscriminator()
                            + " (" + member.getId().asLong() + ")"
                            + "\nAvatar URL: "
                            + memberAvatar;
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .subscribe(System.out::println);

        //Commands:

        //Avatar (ID: 1):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "avatar")
                        || message.getContent().toLowerCase().contains(prefix + "a")
                        || message.getContent().toLowerCase().contains(prefix + "pfp")
                        || message.getContent().toLowerCase().contains(prefix + "profilepicture")
                        || message.getContent().toLowerCase().contains(prefix + "profile"))
                .map(Message -> {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    String[] args = Message.getContent().split("\\s");
                    AtomicBoolean success = new AtomicBoolean(false);

                    if (isAllowed(1,userRolesList,member,guildOwnerID)){
                        if (args.length < 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                        }else{
                            String userId = args[1];
                            if (args[1].contains("@") || args[1].contains("#")){
                                userId = userId.replaceAll("[^\\d.]", "");
                            }
                            try{
                                String userAvatar = Objects.requireNonNull(Objects.requireNonNull(Message.getGuild().block()).getMemberById(Snowflake.of(userId)).block()).getAvatarUrl();
                                Message.getChannel().flatMap(channel -> channel.createMessage("Avatar/Profile Picture Link: " + userAvatar)).block();
                                success.set(true);
                            }catch (Exception e){
                                Message.getChannel().flatMap(channel -> channel.createMessage("Error while getting avatar.")).block();
                            }
                        }
                        return "Command Ran: (Avatar) Success: " + success.get();
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Permissions)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);


        //Blacklist add (ID: 2):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "blacklist add")
                        || message.getContent().toLowerCase().contains(prefix + "bl add"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(2,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split(" (?=(([^'\"]*['\"]){2})*[^'\"]*$)");
                        AtomicBoolean success = new AtomicBoolean(false);

                        if (args.length <= 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                        }else{
                            if (args[2] != null){
                                String blacklistArg = args[2];
                                String typeArg = "Undefined";

                                if (!(3 >= args.length) && !args[3].isEmpty()){
                                    typeArg = args[3];
                                }

                                PreparedStatement preparedStatement = null;
                                try{
                                    if (!blacklistArg.equals("")){
                                        String query = "INSERT into avatar_blacklist (avatar_url, type) values (?,?)";

                                        preparedStatement = database.connect().prepareStatement(query);
                                        preparedStatement.setString(1,blacklistArg);
                                        preparedStatement.setString(2,typeArg);

                                        preparedStatement.execute();
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while adding to blacklist.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    imageRecognition.populateBlacklist();
                                    success.set(true);
                                    Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Blacklist successfully updated.")).block();
                                }
                            }
                        }

                        return "Command Ran: (Blacklist Add)" + Arrays.toString(args) + " Success: " + success;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Blacklist Add)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Blacklist remove (ID: 3):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "blacklist remove")
                        || message.getContent().toLowerCase().contains(prefix + "bl remove"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(3,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split(" (?=(([^'\"]*['\"]){2})*[^'\"]*$)");
                        AtomicBoolean success = new AtomicBoolean(false);

                        if (args.length <= 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                            success.set(false);
                        }else{
                            if (args[2] != null){
                                String removeBlackListArg = args[2];

                                PreparedStatement preparedStatement = null;
                                try{
                                    if (!removeBlackListArg.equals("") && isURLValid(removeBlackListArg)){
                                        String query = "DELETE FROM avatar_blacklist WHERE avatar_url = ?";

                                        preparedStatement = database.connect().prepareStatement(query);
                                        preparedStatement.setString(1,removeBlackListArg);

                                        preparedStatement.execute();
                                        if(preparedStatement.getUpdateCount() >= 1){
                                            Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Blacklist item(s) removed.")).block();
                                        }else{
                                            Message.getChannel().flatMap(channel -> channel.createMessage(":x: No match found.")).block();
                                        }
                                        success.set(true);
                                    }else{
                                        if (!removeBlackListArg.equals("")){
                                            String query = "DELETE FROM avatar_blacklist WHERE type = ? OR avatar_url = ?";

                                            preparedStatement = database.connect().prepareStatement(query);
                                            preparedStatement.setString(1,removeBlackListArg);
                                            preparedStatement.setString(2,removeBlackListArg);

                                            preparedStatement.execute();
                                            if(preparedStatement.getUpdateCount() >= 1){
                                                Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Blacklist item(s) removed.")).block();
                                            }else{
                                                Message.getChannel().flatMap(channel -> channel.createMessage(":x: No match found.")).block();
                                            }
                                        }
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while removing from blacklist.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    imageRecognition.populateBlacklist();
                                    success.set(true);
                                }
                            }
                        }

                        return "Command Ran: (Blacklist Remove)" + Arrays.toString(args) + " Success: " + success.get();
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Blacklist Remove)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Blacklist search (ID: 4):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "blacklist search")
                        || message.getContent().toLowerCase().contains(prefix + "bl search"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(4,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split(" (?=(([^'\"]*['\"]){2})*[^'\"]*$)");

                        AtomicBoolean success = new AtomicBoolean(false);
                        AtomicBoolean foundResult = new AtomicBoolean(false);

                        Optional<Snowflake> userSnowflake = Message.getAuthor().map(User::getId);
                        long userID = userSnowflake.orElseThrow().asLong();

                        String searchToken;

                        if (2 >= args.length) {
                            searchToken = "";
                        }else {
                            searchToken = args[2];
                        }

                        PreparedStatement preparedStatement = null;
                        ResultSet resultSet = null;

                        try{
                            if (!searchToken.equals("")){
                                String query;
                                boolean isTypeSearch = false;

                                if (isURLValid(searchToken)){
                                    query = "SELECT * FROM avatar_blacklist WHERE avatar_url = ?";
                                }else{
                                    isTypeSearch = true;
                                    query = "SELECT * FROM avatar_blacklist WHERE type = ?";
                                }

                                preparedStatement = database.connect().prepareStatement(query);
                                preparedStatement.setString(1,searchToken);

                                resultSet = preparedStatement.executeQuery();

                                if(isTypeSearch){
                                    int count = 0;
                                    while(resultSet.next()){
                                        count += 1;
                                    }
                                    if (count == 0){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":x: <@"
                                                + userID + ">"
                                                + " No entries found with type: " + searchToken)
                                        ).block();
                                        foundResult.set(false);
                                    }else{
                                        int finalCount = count;
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":pencil: :ballot_box_with_check: <@"
                                                + userID + "> "
                                                + "**" + finalCount + "** entries found. With type: `" + searchToken + "`")
                                        ).block();
                                        foundResult.set(true);
                                    }
                                }else{
                                    if (resultSet.next()){
                                        ResultSet finalResultSet = resultSet;

                                        Message.getChannel().flatMap(channel -> {
                                            try {
                                                foundResult.set(true);
                                                return channel.createMessage(":pencil: :ballot_box_with_check: <@"
                                                        + userID + ">"
                                                        + " The provided token is in the database. For type: "
                                                        + "`" + finalResultSet.getString(3) + "`");
                                            }catch (SQLException e) {
                                                e.printStackTrace();
                                            }
                                            foundResult.set(false);
                                            success.set(false);
                                            return channel.createMessage(":pencil: <@"
                                                    + Message.getAuthor().map(User -> User.getId().asLong()) + ">"
                                                    + "`Query Error`" );
                                        }).block();
                                    }else{
                                        foundResult.set(false);
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":x: <@"
                                                + userID + ">"
                                                + " no entries found. With avatar: `" + searchToken + "`")
                                        ).block();
                                    }
                                }
                            } else {
                                String query = "SELECT * FROM avatar_blacklist";
                                Statement statement = database.connect().createStatement();
                                resultSet = statement.executeQuery(query);

                                ArrayList<String> pasteDescription = new ArrayList<>();
                                while (resultSet.next()){
                                    pasteDescription.add(resultSet.getString(2)
                                            + " Type: " + resultSet.getString(3)
                                    );
                                }
                                Pastee pastee = new Pastee();
                                pastee.setApiKey(propertyValues.getPasteAPI());
                                String pasteLink = pastee.buildBlackList(pasteDescription);

                                Message.getChannel().flatMap(channel -> channel.createMessage(":pencil: <@"
                                        + userID + ">"
                                        + " Blacklist: " + pasteLink)
                                ).block();
                                foundResult.set(true);

                            }
                        }catch (Exception e){
                            e.printStackTrace();
                            Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while searching blacklist.")).block();
                            foundResult.set(false);
                            success.set(false);
                        }finally {
                            database.disconnect();
                            if (preparedStatement != null){
                                try {
                                    preparedStatement.close();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (resultSet != null){
                                try {
                                    resultSet.close();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                            success.set(true);
                        }
                        return "Command Ran: (Blacklist Search)" + Arrays.toString(args) + " Success: " + success + " Result: " + foundResult;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Blacklist Search)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Commands  (ID: 5):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> (message.getContent().toLowerCase().contains(prefix + "help")
                        || message.getContent().toLowerCase().contains(prefix + "cmds")
                        || message.getContent().toLowerCase().contains(prefix + "cmd")
                        || message.getContent().toLowerCase().contains(prefix + "command")
                        || message.getContent().toLowerCase().contains(prefix + "commands"))
                        && !(message.getContent().toLowerCase().contains("info")))
                .map(Message -> {
                    Mono<Member> member;
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    member = Message.getAuthorAsMember();
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(5, userRolesList, member, guildOwnerID )){
                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                embedCreateSpec.setColor(Color.MEDIUM_SEA_GREEN)
                                        .setTitle("Asailify Commands")
                                        .addField(prefix + "avatar","Args: `[Snowflake*]`\nId: 1",true)

                                        .addField(prefix + "blacklist add","Args: `[URL*, Type]`\nId: 2",true)
                                        .addField(prefix + "blacklist remove", "Args: `[URL*/Type*]`\nId: 3",true)
                                        .addField(prefix + "blacklist search", "Args: `[URL/Type]`\nId: 4",true)

                                        .addField(prefix + "commands","Args: `[N/A]`\nId: 5",true)
                                        .addField(prefix + "commands info", "Args: `[CommandId]`\nId: 6",true)

                                        .addField(prefix + "ignore add", "Args: `[Snowflake*]`\nId: 7",true)
                                        .addField(prefix + "ignore remove", "Args: `[Snowflake*]`\nId: 8",true)
                                        .addField(prefix + "ignore search", "Args: `[Snowflake]`\nId: 9",true)

                                        .addField(prefix + "notify add","Args: `[Channel*/User*/Role*, Snowflake*]`\nId: 10",true)
                                        .addField(prefix + "notify remove", "Args: `[Snowflake*]`\nId: 11", true)
                                        .addField(prefix + "notify search", "Args: `[Snowflake]`\nId: 12",true)

                                        .addField(prefix + "permissions add", "Args: `[CommandID*, Snowflake*, AllowHigherRoles]`\nId: 13",true)
                                        .addField(prefix + "permissions remove", "Args: `[CommandId*, Snowflake*]`\nId: 14",true)
                                        .addField(prefix + "permissions", "Args: `[N/A]`\nId: 15",true)

                                        .addField(prefix + "prefix set", "Args: `[String*]`\nId: 16",true)

                                        .addField(prefix + "scan", "Args: `[All*/Snowflake*]`\nId: 17",true)

                                        .addField(prefix + "similarity set","Args: `[Double*]`\nId: 18",true)
                                        .addField(prefix + "similarity search","Args: `[N/A]`\nId: 19",true)

                                        .addField("\u200E","\u200E",true)
                                        .addField("\u200E","\u200E",true)
                                        .setFooter(" (*) = Required, (/) = Or ","")
                                        .setTimestamp(Instant.now())
                        )).block();
                        return "Command Ran: (Help/Commands)";
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid permissions.")).block();
                        return "Command Denied: (Help/Commands)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Commands info (ID: 6):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "commands info")
                        || message.getContent().toLowerCase().contains(prefix + "cmds info")
                        || message.getContent().toLowerCase().contains(prefix + "cmd info")
                        || message.getContent().toLowerCase().contains(prefix + "command info"))
                .map(Message -> {
                    Mono<Member> member;
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    member = Message.getAuthorAsMember();
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(6, userRolesList, member, guildOwnerID )){
                        String[] args = Message.getContent().split(" (?=(([^'\"]*['\"]){2})*[^'\"]*$)");

                        if (args.length <= 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                        }else{
                            if (args[2] != null){

                                if (args[2].contains("\"")){
                                    args[2] = args[2].replaceAll("^\"|\"$", "");
                                }

                                switch (args[2].toLowerCase()){
                                    case "avatar":
                                    case "1":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"avatar\nId: 1",false)
                                                        .addField("Alias:",prefix + "a, "
                                                                + prefix + "pfp, "
                                                                + prefix + "profilepicture, "
                                                                + prefix + "profile",false)
                                                        .addField("Description:","Returns a link of user avatar",false)
                                                        .addField("Arguments:","[Snowflake*]: ID of the user, you may also @ the user.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "avatar 12345679010\n"
                                                                + prefix + "avatar @!user\n",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "blacklist add":
                                    case "bl add":
                                    case "2":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"blacklist add\nId: 2",false)
                                                        .addField("Alias:",prefix + "bl add",false)
                                                        .addField("Description:","Adds an image to the blacklist database",false)
                                                        .addField("Arguments:","[URL*]: Image URL.\n[Type]: Title or type, to identify the image in one word with no spaces.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "blacklist add https://cdn.discordapp.com/avatars/123/123.png BaconRaid\n"
                                                                + prefix + "blacklist add https://cdn.discordapp.com/avatars/123/123.png\n"
                                                                + prefix + "blacklist add https://cdn.discordapp.com/avatars/123/123.webp?size=1024",false)
                                                        .setFooter("(*) = Required, (/) = Or","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "blacklist remove":
                                    case "bl remove":
                                    case "3":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"blacklist remove\nId: 3",false)
                                                        .addField("Alias:",prefix + "bl remove",false)
                                                        .addField("Description:","Removes an image from the blacklist database, you can do blacklist search to get the list of images on the database.",false)
                                                        .addField("Arguments:","[URL\\*/Type*]: Image URL OR Type token, using type will remove all entries that have the same type title.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "blacklist remove https://cdn.discordapp.com/avatars/123/123.png\n"
                                                                + prefix + "blacklist remove BaconRaid\n"
                                                                + prefix + "bl remove BaconRaid",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "blacklist search":
                                    case "bl search":
                                    case "4":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"blacklist search\nId: 4",false)
                                                        .addField("Alias:",prefix + "bl search",false)
                                                        .addField("Description:","Returns true if in database, or the number of entries with given type in the database, or returns a list of items in database if you provide no arguments.",false)
                                                        .addField("Arguments:","[URL/Type]: Image URL or type token",false)
                                                        .addField("Examples: ", ">>> " + prefix + "blacklist search https://cdn.discordapp.com/avatars/123/123.png\n"
                                                                + prefix + "blacklist search\n"
                                                                + prefix + "blacklist search BaconRaid",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "commands":
                                    case "cmds":
                                    case "help":
                                    case "5":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"commands\nId: 5",false)
                                                        .addField("Alias:",prefix + "command, "
                                                                + prefix + "cmds, "
                                                                + prefix + "cmd, "
                                                                + prefix + "help",false)
                                                        .addField("Description:","Returns a list of commands.",false)
                                                        .addField("Arguments:","[N/A]",false)
                                                        .addField("Examples: ", ">>> " + prefix + "commands\n"
                                                                + prefix + "cmds",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "commands info":
                                    case "cmds info":
                                    case "cmd info":
                                    case "6":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"commands info\nId: 6",false)
                                                        .addField("Alias:",prefix + "command info, "
                                                                + prefix + "cmds info, "
                                                                + prefix + "cmd info",false)
                                                        .addField("Description:","Returns more information and detail of a certain command.",false)
                                                        .addField("Arguments:","[CommandId*]: The id of the command, a command id can be found using \"" + prefix + "commands\"",false)
                                                        .addField("Examples: ", ">>> " + prefix + "commands info 1\n"
                                                                + prefix + "cmds info 1",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "ignore add":
                                    case "7":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"ignore add\nId: 7",false)
                                                        .addField("Description:","Adds a user to the ignore list, these users profiles will not be scanned and or notified if match",false)
                                                        .addField("Arguments:","[Snowflake*]: ID of the user, you may also @ the user.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "ignore add 12345679010\n"
                                                                + prefix + "ignore add @!user\n",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "ignore remove":
                                    case "8":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"ignore remove\nId: 8",false)
                                                        .addField("Description:","Removes a user from the ignore list.",false)
                                                        .addField("Arguments:","[Snowflake*]: ID of the user, you may also @ the user.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "ignore remove 12345679010\n"
                                                                + prefix + "ignore remove @!user\n",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "ignore search":
                                    case "9":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"ignore search\nId: 9",false)
                                                        .addField("Description:","Returns true if in database, or returns a list of items in database if you provide no arguments.",false)
                                                        .addField("Arguments:","[Snowflake]: ID of the user, you may also @ the user.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "ignore search 12345679010\n"
                                                                + prefix + "ignore search @!user\n"
                                                                + prefix + "ignore search",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "notify add":
                                    case "notifications add":
                                    case "10":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"notify add\nId: 10",false)
                                                        .addField("Alias:",prefix + "notifications add",false)
                                                        .addField("Description:","Adds a user/channel/role to the notify list.",false)
                                                        .addField("Arguments:","[Channel\\*\\/User\\*\\/Role\\*]: Defines the type of notification.\n[Snowflake*]: ID of the user/channel/role",false)
                                                        .addField("Examples: ", ">>> " + prefix + "notify add channel 123\n"
                                                                + prefix + "notify add user 123\n"
                                                                + prefix + "notify add u 123\n"
                                                                + prefix + "notify add channel 12345678910"
                                                                + prefix + "notify add role 123415",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "notify remove":
                                    case "notifications remove":
                                    case "11":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"notify remove\nId: 11",false)
                                                        .addField("Alias:",prefix + "notifications remove",false)
                                                        .addField("Description:","Removes an item from the notify list.",false)
                                                        .addField("Arguments:","[Snowflake*]: ID of the user/channel/role",false)
                                                        .addField("Examples: ", ">>> " + prefix + "notify remove 123\n"
                                                                + prefix + "notify remove u @!123",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "12":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"notify search\nId: 12",false)
                                                        .addField("Alias:",prefix + "notifications search",false)
                                                        .addField("Description:","Returns true if in database, or the number of entries with given type, or returns a list of items in database if you provide no arguments.",false)
                                                        .addField("Arguments:","[Snowflake]: ID of the user/channel/role",false)
                                                        .addField("Examples: ", ">>> " + prefix + "notify search 123\n"
                                                                + prefix + "notify search\n",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "permissions add":
                                    case "perms add":
                                    case "perm add":
                                    case "13":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"permissions add\nId: 13",false)
                                                        .addField("Alias:",">>> " + prefix + "permission add, "
                                                                + prefix + "perms add, "
                                                                + prefix + "perm add",false)
                                                        .addField("Description:","Adds a permitted role for an individual command. Users with this permitted role are allowed to execute the command.",false)
                                                        .addField("Arguments:","[CommandID\\*]: Id of the command, use \"" + prefix + "commands\" to find command Id's\n"
                                                                + "[SnowFlake*]: Snowflake of the Role, or you can @!Role, beware you may mention the role.\n"
                                                                + "[AllowHigherRoles]: True/yes/1/enabled OR false/no/0/disabled, if true users with higher roles are able to execute the command.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "permissions add 1 150093661231775744 true\n"
                                                                + prefix + "perm add 1 150093661231775744",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "permissions remove":
                                    case "perm remove":
                                    case "perms remove":
                                    case "14":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"permissions remove\nId: 14",false)
                                                        .addField("Alias:",prefix + "permission remove, "
                                                                + prefix + "perm remove, "
                                                                + prefix + "perms remove",false)
                                                        .addField("Description:","Removes a permitted role from a command.",false)
                                                        .addField("Arguments:","[CommandId\\*]: The ID of the command.\n[Snowflake*]: The Id of the role.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "permissions remove 1 150093661231775744\n"
                                                                + prefix + "perms remove 1 150093661231775744",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "permissions":
                                    case "perms":
                                    case "perm":
                                    case "15":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"permissions\nId: 15",false)
                                                        .addField("Alias:",prefix + "perms, "
                                                                + prefix + "perm",false)
                                                        .addField("Description:","Returns a message containing all commands with corresponding permitted roles.",false)
                                                        .addField("Arguments:","[N/A]",false)
                                                        .addField("Examples: ", ">>> " + prefix + "permissions\n"
                                                                + prefix + "perms",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "prefix set":
                                    case "16":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"prefix set\nId: 16",false)
                                                        .addField("Description:","Updates the command prefix.",false)
                                                        .addField("Arguments:","[String*]: A character/string.",false)
                                                        .addField("Examples: ", ">>> " + prefix + "prefix set !\n"
                                                                + prefix + "prefix set ~\n",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "scan":
                                    case "scan all":
                                    case "scan custom":
                                    case "17":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"scan\nId: 17",false)
                                                        .addField("Description:","Scans a particular user or all users in the guild and notifies upon blacklist match." +
                                                                "\nThe bot uses a divide-and-conquer algorithm in order to scan users efficiently, you can run a custom scan to set the divisor while scanning." +
                                                                "\nThe divisor decides how many times the bot will split up the user list and scan each sub-group at the same time. The higher the divisor, the faster the scan. However do not set divisor higher than the guild member count otherwise the scan will fail." +
                                                                "\nYou can also custom scan a single image, instead of searching against the entire blacklist.",false)
                                                        .addField("Arguments:","[Snowflake]: User ID you wish to scan." +
                                                                "\n[All]: If you wish to scan all users." +
                                                                "\n[Legacy]: Linear algorithm search" +
                                                                "\n[Custom]: Scan for a single image (URL), or scan with a set divisor number (int).",false)
                                                        .addField("Examples: ", ">>> "
                                                                + prefix + "scan @!123\n"
                                                                + prefix + "scan 127932752744939521\n"
                                                                + prefix + "scan all\n"
                                                                + prefix + "scan legacy"
                                                                + prefix + "scan custom 100\n"
                                                                + prefix + "scan custom 12341.png",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "similarity set":
                                    case "sim set":
                                    case "s set":
                                    case "18":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"similarity set\nId: 18",false)
                                                        .addField("Alias:",prefix + "sim set, "
                                                                + prefix + "s set",false)
                                                        .addField("Description:","Sets the similarity index. Whenever a user's avatar is checked, pixel similarity takes place against all images in the blacklist, and stores a similarity index for each.\nIf one of those indices is lower than the set index, it notifies as a match.\nIf there are false positives, lowering the index will help reduce that.",false)
                                                        .addField("Arguments:","[Double*]: Decimal or Integer",false)
                                                        .addField("Examples: ", ">>> " + prefix + "similarity set 13\n"
                                                                ,false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                    case "similarity search":
                                    case "similarity":
                                    case "sim":
                                    case "sim search":
                                    case "19":
                                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                                embedCreateSpec.setColor(Color.CYAN)
                                                        .setTitle("Command Information")
                                                        .addField("Command:",prefix +"similarity search\nId: 19",false)
                                                        .addField("Alias:",prefix + "similarity, "
                                                                + prefix + "sim, "
                                                                + prefix + "sim search",false)
                                                        .addField("Description:","Returns the current similarity index.",false)
                                                        .addField("Arguments:","[N/A]",false)
                                                        .addField("Examples: ", ">>> " + prefix + "similarity search",false)
                                                        .setFooter(" (*) = Required, (/) = Or ","")
                                                        .setTimestamp(Instant.now())
                                        )).block();
                                        break;
                                }
                            }
                        }
                        return "Command Ran: (Command info) Args: " + Arrays.toString(args);
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid permissions.")).block();
                        return "Command Denied: (Command info)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Ignore add (ID: 7):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "ignore add"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(7,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");
                        AtomicBoolean success = new AtomicBoolean(false);

                        if (args.length <= 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                        }else{
                            if (args[2] != null){
                                String id = args[2];

                                if (args[2].contains("@") || args[2].contains("#")){
                                    id = id.replaceAll("[^\\d.]", "");
                                }

                                PreparedStatement preparedStatement = null;

                                try {
                                    String query = "INSERT into ignore_list (id) values (?)";
                                    preparedStatement = database.connect().prepareStatement(query);
                                    preparedStatement.setString(1,id);

                                    preparedStatement.execute();
                                    success.set(true);
                                }catch (SQLIntegrityConstraintViolationException e){
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred, duplicate entry.")).block();
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while adding to database.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (success.get()){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Ignore token successfully updated.")).block();
                                        updateIgnoreList();
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Ignore Add)" + Arrays.toString(args) + " Success: " + success.get();
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Blacklist Add)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Ignore remove (ID: 8):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "ignore remove"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(8,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");
                        AtomicBoolean success = new AtomicBoolean(false);

                        if (args.length <= 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                        }else{
                            if (args[2] != null){
                                String removeArg = args[2];

                                if (args[2].contains("@") || args[2].contains("#")){
                                    removeArg = removeArg.replaceAll("[^\\d.]", "");
                                }

                                PreparedStatement preparedStatement = null;

                                try {
                                    String query = "DELETE FROM ignore_list WHERE id = ?";
                                    preparedStatement = database.connect().prepareStatement(query);
                                    preparedStatement.setString(1,removeArg);

                                    preparedStatement.execute();
                                    if(preparedStatement.getUpdateCount() >= 1){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Ignore token successfully removed.")).block();
                                    }else{
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":x: No match found.")).block();
                                    }

                                    success.set(true);
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while removing from database.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (success.get()){
                                        updateIgnoreList();
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Ignore Remove)" + Arrays.toString(args) + " Success: " + success.get();
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Blacklist Add)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Ignore search (ID: 9):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "ignore search"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(9,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");

                        AtomicBoolean success = new AtomicBoolean(false);
                        AtomicBoolean foundResult = new AtomicBoolean(false);

                        Optional<Snowflake> userSnowflake = Message.getAuthor().map(User::getId);
                        long userID = userSnowflake.orElseThrow().asLong();

                        String searchToken;

                        if (2 >= args.length) {
                            searchToken = "";
                        }else {
                            searchToken = args[2];
                            if (args[2].contains("@") || args[2].contains("#")){
                                searchToken = searchToken.replaceAll("[^\\d.]", "");
                            }
                        }

                        PreparedStatement preparedStatement = null;
                        ResultSet resultSet = null;

                        try{
                            if (!searchToken.equals("")){
                                String query = "SELECT * FROM ignore_list WHERE id = ?";

                                preparedStatement = database.connect().prepareStatement(query);
                                preparedStatement.setString(1,searchToken);

                                resultSet = preparedStatement.executeQuery();

                                if (resultSet.next()){
                                    foundResult.set(true);
                                    Message.getChannel().flatMap(channel ->  channel.createMessage(":pencil: :ballot_box_with_check: <@"
                                            + userID + ">"
                                            + " The provided token is in the Ignore database.")).block();
                                }else{
                                    foundResult.set(false);
                                    String finalSearchToken = searchToken;
                                    Message.getChannel().flatMap(channel -> channel.createMessage(":x: <@"
                                            + userID + ">"
                                            + " no entries found. With token: `" + finalSearchToken + "`")
                                    ).block();
                                }
                            }else{
                                String query = "SELECT * FROM ignore_list";
                                Statement statement = database.connect().createStatement();
                                resultSet = statement.executeQuery(query);

                                ArrayList<String> pasteDescription = new ArrayList<>();
                                while (resultSet.next()){
                                    pasteDescription.add(resultSet.getString(1));
                                }
                                if (!pasteDescription.isEmpty()){
                                    Pastee pastee = new Pastee();
                                    pastee.setApiKey(propertyValues.getPasteAPI());
                                    String pasteLink = pastee.buildIgnoreList(pasteDescription);

                                    Message.getChannel().flatMap(channel -> channel.createMessage(":pencil: <@"
                                            + userID + ">"
                                            + " Ignore list: " + pasteLink)
                                    ).block();
                                    foundResult.set(true);
                                }else{
                                    Message.getChannel().flatMap(channel -> channel.createMessage(":x: <@"
                                            + userID + ">" + " Ignore list is empty"))
                                            .block();
                                    foundResult.set(false);
                                }
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                            Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while searching ignore list.")).block();
                            foundResult.set(false);
                            success.set(false);
                        }finally {
                            database.disconnect();
                            if (preparedStatement != null){
                                try {
                                    preparedStatement.close();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (resultSet != null){
                                try {
                                    resultSet.close();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                            success.set(true);
                        }
                        return "Command Ran: (Ignore Search)" + Arrays.toString(args) + " Success: " + success + " Result: " + foundResult;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Ignore Search)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Notify add (ID: 10):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "notify add")
                        || message.getContent().toLowerCase().contains(prefix + "notifications add"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(10,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");
                        AtomicBoolean success = new AtomicBoolean(false);

                        if (args.length <= 3){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                            success.set(false);
                        }else{
                            if (args[3] != null){
                                String notifyID = args[3];
                                String notifyType = args[2];

                                if (args[3].contains("@") || args[3].contains("#")){
                                    notifyID = notifyID.replaceAll("[^\\d.]", "");
                                }

                                int notifyTypeID;

                                PreparedStatement preparedStatement = null;

                                if (notifyType.equalsIgnoreCase("channel")
                                        || notifyType.equalsIgnoreCase("channels")
                                        || notifyType.equalsIgnoreCase("c")){
                                    notifyTypeID = 0;
                                }else if (notifyType.equalsIgnoreCase("user")
                                        || notifyType.equalsIgnoreCase("users")
                                        || notifyType.equalsIgnoreCase("u")){
                                    notifyTypeID = 1;
                                }else if (notifyType.equalsIgnoreCase("role")
                                        || notifyType.equalsIgnoreCase("roles")
                                        || notifyType.equalsIgnoreCase("r")){
                                    notifyTypeID = 2;
                                }else{
                                    notifyTypeID = 3;
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Invalid notify type.")).block();
                                    success.set(false);
                                }

                                try {
                                    if (notifyTypeID != 3) {
                                        String query = "INSERT into notify (id, type) values (?,?)";

                                        preparedStatement = database.connect().prepareStatement(query);
                                        preparedStatement.setString(1, notifyID);
                                        preparedStatement.setInt(2, notifyTypeID);

                                        preparedStatement.execute();
                                        success.set(true);
                                    }
                                }catch (SQLIntegrityConstraintViolationException e){
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred, duplicate entry.")).block();
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while adding to database.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (notifyTypeID != 3 && success.get()){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Notification token successfully added.")).block();
                                        updateNotifyList(client);
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Notify Add)" + Arrays.toString(args) + " Success: " + success;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Notify add)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Notify remove (ID: 11):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "notify remove")
                        || message.getContent().toLowerCase().contains(prefix + "notifications remove"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(11,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");
                        AtomicBoolean success = new AtomicBoolean(false);

                        if (args.length < 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                            success.set(false);
                        }else{
                            if (args[2] != null){
                                String removeNotifyArg = args[2];

                                if (args[2].contains("@") || args[2].contains("#")){
                                    removeNotifyArg = removeNotifyArg.replaceAll("[^\\d.]", "");
                                }

                                PreparedStatement preparedStatement = null;
                                try{
                                    if (!removeNotifyArg.equals("")){
                                        String query = "DELETE FROM notify WHERE id = ?";

                                        preparedStatement = database.connect().prepareStatement(query);
                                        preparedStatement.setString(1,removeNotifyArg);

                                        preparedStatement.execute();

                                        if(preparedStatement.getUpdateCount() >= 1){
                                            Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Notify item removed.")).block();
                                        }else{
                                            Message.getChannel().flatMap(channel -> channel.createMessage(":x: No match found.")).block();
                                        }

                                        success.set(true);
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while removing from notify database.")).block();
                                    success.set(false);
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (success.get()){
                                        updateNotifyList(client);
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Notify Remove)" + Arrays.toString(args) + " Success: " + success.get();
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Notify Remove)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Notify search (ID: 12):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "notify search")
                        || message.getContent().toLowerCase().contains(prefix + "notifications search"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(12,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");

                        AtomicBoolean success = new AtomicBoolean(false);
                        AtomicBoolean foundResult = new AtomicBoolean(false);

                        Optional<Snowflake> userSnowflake = Message.getAuthor().map(User::getId);
                        long userID = userSnowflake.orElseThrow().asLong();

                        String searchToken;

                        if (2 >= args.length) {
                            searchToken = "";
                        }else {
                            searchToken = args[2];
                        }

                        if (searchToken.equalsIgnoreCase("user")
                                || searchToken.equalsIgnoreCase("users")
                                || searchToken.equalsIgnoreCase("u")){
                            searchToken = "1";
                        }else if (searchToken.equalsIgnoreCase("channel")
                                || searchToken.equalsIgnoreCase("channels")
                                || searchToken.equalsIgnoreCase("c")){
                            searchToken = "0";
                        }

                        PreparedStatement preparedStatement = null;
                        ResultSet resultSet = null;

                        try{
                            if (!searchToken.equals("")){
                                String query;
                                boolean isTypeSearch = false;

                                if (onlyDigits(searchToken) && searchToken.length() == 1 ){
                                    isTypeSearch = true;
                                    query = "SELECT * FROM notify WHERE type = ?";
                                    preparedStatement = database.connect().prepareStatement(query);
                                    preparedStatement.setInt(1,Integer.parseInt(searchToken));
                                }else{
                                    query = "SELECT * FROM notify WHERE id = ?";
                                    preparedStatement = database.connect().prepareStatement(query);
                                    preparedStatement.setString(1,searchToken);
                                }

                                resultSet = preparedStatement.executeQuery();

                                if(isTypeSearch){
                                    int count = 0;
                                    while(resultSet.next()){
                                        count += 1;
                                    }
                                    if (count == 0){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":x: <@"
                                                + userID + ">"
                                                + " No entries found with type: " + args[2])
                                        ).block();
                                        foundResult.set(false);
                                    }else{
                                        int finalCount = count;
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":pencil: :ballot_box_with_check: <@"
                                                + userID + "> "
                                                + "**" + finalCount + "** entries found. With type: `" + args[2] + "`")
                                        ).block();
                                        foundResult.set(true);
                                    }
                                }else{
                                    if (resultSet.next()){
                                        foundResult.set(true);
                                        Message.getChannel().flatMap(channel ->  channel.createMessage(":pencil: :ballot_box_with_check: <@"
                                                + userID + ">"
                                                + " The provided token is in the Notification database.")).block();
                                    }else{
                                        foundResult.set(false);
                                        String finalSearchToken = searchToken;
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":x: <@"
                                                + userID + ">"
                                                + " no entries found. With type: `" + finalSearchToken + "`")
                                        ).block();
                                    }
                                }
                            } else {
                                String query = "SELECT * FROM notify";
                                Statement statement = database.connect().createStatement();
                                resultSet = statement.executeQuery(query);

                                ArrayList<String> pasteDescription = new ArrayList<>();
                                pasteDescription.add("Type: 0 = Channel, 1 = User, 2 = Role\n");
                                while (resultSet.next()){
                                    pasteDescription.add(resultSet.getString(1)
                                            + " Type: " + resultSet.getString(2)
                                    );
                                }
                                Pastee pastee = new Pastee();
                                pastee.setApiKey(propertyValues.getPasteAPI());
                                String pasteLink = pastee.buildNotifyList(pasteDescription);

                                Message.getChannel().flatMap(channel -> channel.createMessage(":pencil: <@"
                                        + userID + ">"
                                        + " Notification list: " + pasteLink)
                                ).block();
                                foundResult.set(true);

                            }
                        }catch (Exception e){
                            e.printStackTrace();
                            Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while searching notification list.")).block();
                            foundResult.set(false);
                            success.set(false);
                        }finally {
                            database.disconnect();
                            if (preparedStatement != null){
                                try {
                                    preparedStatement.close();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (resultSet != null){
                                try {
                                    resultSet.close();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                            success.set(true);
                        }
                        return "Command Ran: (Search Notify)" + Arrays.toString(args) + " Success: " + success + " Result: " + foundResult;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Notify Search)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Permissions add (ID: 13):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "permissions add")
                        || message.getContent().toLowerCase().contains(prefix + "permission add")
                        || message.getContent().toLowerCase().contains(prefix + "perms add")
                        || message.getContent().toLowerCase().contains(prefix + "perm add"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();
                    AtomicBoolean success = new AtomicBoolean(false);

                    if(isAllowed(13,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");

                        if (args.length <= 3){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                            success.set(false);
                        }else{
                            if (args[3] != null){
                                String commandID = args[2];
                                String roleID = args[3];
                                String allowHigher = "false";
                                if (args.length > 4){
                                    allowHigher = args[4];
                                }


                                if (args[3].contains("@") || args[3].contains("#")){
                                    roleID = roleID.replaceAll("[^\\d.]", "");
                                }

                                int nAllowHigher;

                                PreparedStatement preparedStatement = null;

                                if (allowHigher.equalsIgnoreCase("true")
                                        || allowHigher.equalsIgnoreCase("yes")
                                        || allowHigher.equalsIgnoreCase("1")
                                        || allowHigher.equalsIgnoreCase("enabled")){
                                    nAllowHigher = 1;
                                }else if (allowHigher.equalsIgnoreCase("false")
                                        || allowHigher.equalsIgnoreCase("no")
                                        || allowHigher.equalsIgnoreCase("0")
                                        || allowHigher.equalsIgnoreCase("disabled")){
                                    nAllowHigher = 0;
                                }else{
                                    nAllowHigher = 3;
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Invalid notify type.")).block();
                                    success.set(false);
                                }


                                try {
                                    if (nAllowHigher != 3) {
                                        String query = "INSERT into roles (role, command_id, higher_allowed) values (?,?,?)";

                                        preparedStatement = database.connect().prepareStatement(query);
                                        preparedStatement.setString(1,roleID);
                                        preparedStatement.setString(2, commandID);
                                        preparedStatement.setInt(3, nAllowHigher);

                                        preparedStatement.execute();
                                        success.set(true);
                                    }
                                }catch (SQLIntegrityConstraintViolationException e){
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred, duplicate entry.")).block();
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while adding to database.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (nAllowHigher != 3 && success.get()){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Permission(s) successfully updated.")).block();
                                        updateAllowedRoles();
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Permissions Add)" + Arrays.toString(args) + " Success: " + success;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Perm Add)[Invalid Permissions]";
                    }

                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Permissions remove (ID: 14):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "permissions remove")
                        || message.getContent().toLowerCase().contains(prefix + "permission remove")
                        || message.getContent().toLowerCase().contains(prefix + "perms remove")
                        || message.getContent().toLowerCase().contains(prefix + "perm remove"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(14,userRolesList,member,guildOwnerID)){
                        String[] args = Message.getContent().split("\\s");
                        AtomicBoolean success = new AtomicBoolean(false);

                        if (args.length < 4){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                            success.set(false);
                        }else{
                            if (args[2] != null){
                                String commandIdArg = args[2];
                                String roleIdArg = args[3];

                                if (args[3].contains("@") || args[3].contains("#")){
                                    roleIdArg = roleIdArg.replaceAll("[^\\d.]", "");
                                }

                                PreparedStatement preparedStatement = null;
                                try{
                                    if (!commandIdArg.equals("")){
                                        String query = "DELETE FROM roles WHERE command_id = ? AND role = ?";

                                        preparedStatement = database.connect().prepareStatement(query);
                                        preparedStatement.setString(1,commandIdArg);
                                        preparedStatement.setString(2,roleIdArg);

                                        preparedStatement.execute();

                                        if(preparedStatement.getUpdateCount() >= 1){
                                            Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Role item removed.")).block();
                                            success.set(true);
                                        }else{
                                            Message.getChannel().flatMap(channel -> channel.createMessage(":x: No match found.")).block();
                                        }
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while removing from roles database.")).block();
                                    success.set(false);
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (success.get()){
                                        updateAllowedRoles();
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Role Remove)" + Arrays.toString(args) + " Success: " + success.get();
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Perm Remove)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Permissions (ID: 15):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> (message.getContent().toLowerCase().contains(prefix + "permissions")
                        || message.getContent().toLowerCase().contains(prefix + "permission")
                        || message.getContent().toLowerCase().contains(prefix + "perms")
                        || message.getContent().toLowerCase().contains(prefix + "perm"))
                        && ((!message.getContent().toLowerCase().contains("add")) && !message.getContent().toLowerCase().contains("remove")))
                .map(Message -> {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if (isAllowed(15,userRolesList,member,guildOwnerID)){
                        StringBuilder avatar = new StringBuilder();

                        StringBuilder blacklistAdd = new StringBuilder();
                        StringBuilder blacklistRemove = new StringBuilder();
                        StringBuilder blacklistSearch = new StringBuilder();

                        StringBuilder commands = new StringBuilder();
                        StringBuilder commandsInfo = new StringBuilder();

                        StringBuilder ignoreAdd = new StringBuilder();
                        StringBuilder ignoreRemove = new StringBuilder();
                        StringBuilder ignoreSearch = new StringBuilder();

                        StringBuilder notifyAdd = new StringBuilder();
                        StringBuilder notifyRemove = new StringBuilder();
                        StringBuilder notifySearch = new StringBuilder();

                        StringBuilder permissionsAdd = new StringBuilder();
                        StringBuilder permissionsRemove = new StringBuilder();
                        StringBuilder permissions = new StringBuilder();

                        StringBuilder prefixSet = new StringBuilder();

                        StringBuilder scan = new StringBuilder();

                        StringBuilder simSet = new StringBuilder();
                        StringBuilder simSearch = new StringBuilder();

                        for (RoleObject role : roles) {
                            switch (role.getCommandID()) {
                                case 1:
                                    avatar.append(role.getRoleIDString());
                                    break;
                                case 2:
                                    blacklistAdd.append(role.getRoleIDString());
                                    break;
                                case 3:
                                    blacklistRemove.append(role.getRoleIDString());
                                    break;
                                case 4:
                                    blacklistSearch.append(role.getRoleIDString());
                                    break;
                                case 5:
                                    commands.append(role.getRoleIDString());
                                    break;
                                case 6:
                                    commandsInfo.append(role.getRoleIDString());
                                    break;
                                case 7:
                                    ignoreAdd.append(role.getRoleIDString());
                                    break;
                                case 8:
                                    ignoreRemove.append(role.getRoleIDString());
                                    break;
                                case 9:
                                    ignoreSearch.append(role.getRoleIDString());
                                    break;
                                case 10:
                                    notifyAdd.append(role.getRoleIDString());
                                    break;
                                case 11:
                                    notifyRemove.append(role.getRoleIDString());
                                    break;
                                case 12:
                                    notifySearch.append(role.getRoleIDString());
                                    break;
                                case 13:
                                    permissionsAdd.append(role.getRoleIDString());
                                    break;
                                case 14:
                                    permissionsRemove.append(role.getRoleIDString());
                                    break;
                                case 15:
                                    permissions.append(role.getRoleIDString());
                                    break;
                                case 16:
                                    prefixSet.append(role.getRoleIDString());
                                    break;
                                case 17:
                                    scan.append(role.getRoleIDString());
                                    break;
                                case 18:
                                    simSet.append(role.getRoleIDString());
                                    break;
                                case 19:
                                    simSearch.append(role.getRoleIDString());
                                    break;
                            }
                        }
                        Message.getChannel().flatMap(channel -> channel.createEmbed(embedCreateSpec ->
                                embedCreateSpec.setColor(Color.CYAN)
                                        .setTitle("Command Permissions:")
                                        .addField(prefix + "avatar `Id: 1`", avatar.toString() + "\u200E",true)

                                        .addField(prefix + "blacklist add `Id: 2`", blacklistAdd.toString() + "\u200E",true)
                                        .addField(prefix + "blacklist remove `Id: 3`", blacklistRemove.toString() + "\u200E",true)
                                        .addField(prefix + "blacklist search `Id: 4`",  blacklistSearch.toString() + "\u200E",true)

                                        .addField(prefix + "commands `Id: 5`", commands.toString() + "\u200E",true)
                                        .addField(prefix + "commands info `Id: 6`",commandsInfo.toString() + "\u200E", true)

                                        .addField(prefix + "ignore add `Id: 7`",ignoreAdd.toString() + "\u200E", true)
                                        .addField(prefix + "ignore remove `Id: 8`",ignoreRemove.toString() + "\u200E", true)
                                        .addField(prefix + "ignore search `Id: 9`",ignoreSearch.toString() + "\u200E", true)

                                        .addField(prefix + "notify add `Id: 10`", notifyAdd.toString() + "\u200E",true)
                                        .addField(prefix + "notify remove `Id: 11`",  notifyRemove.toString() + "\u200E",true)
                                        .addField(prefix + "notify search `Id: 12`",  notifySearch.toString() + "\u200E",true)

                                        .addField(prefix + "permission add `Id: 13`",  permissionsAdd.toString() + "\u200E",true)
                                        .addField(prefix + "permission remove `Id: 14`",  permissionsRemove.toString() + "\u200E",true)
                                        .addField(prefix + "permissions `Id: 15`", permissions.toString() + "\u200E",true)

                                        .addField(prefix + "prefix set `Id: 16`", prefixSet.toString() + "\u200E",true)
                                        
                                        .addField(prefix + "scan `Id: 17`", scan.toString() + "\u200E",true)

                                        .addField(prefix + "similarity set `Id: 18`", simSet.toString() + "\u200E",true)
                                        .addField(prefix + "similarity search `Id: 19`", simSearch.toString() + "\u200E",true)

                                        .addField("\u200E","\u200E",true)
                                        .addField("\u200E","\u200E",true)
                                        .setFooter(" (+) = Any higher role, (-) = Strictly these roles","")
                                        .setTimestamp(Instant.now())
                        )).block();
                        return "Command Ran: (Permissions)";
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Permissions)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty(); })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Prefix Set (ID: 16):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "prefix set"))
                .map(Message ->  {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(16,userRolesList,member,guildOwnerID)){
                        AtomicBoolean success = new AtomicBoolean(false);
                        String[] args = Message.getContent().split("\\s");

                        if (args.length <= 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                            success.set(false);
                        }else{
                            if (args[2] != null){
                                String prefixArg = args[2];

                                PreparedStatement preparedStatement = null;

                                try {
                                    String query = "UPDATE properties set similarity_index_prefix = ? where id = 2";

                                    preparedStatement = database.connect().prepareStatement(query);
                                    preparedStatement.setString(1, prefixArg);

                                    preparedStatement.execute();
                                    success.set(true);

                                }catch (SQLIntegrityConstraintViolationException e){
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred. Error:" + e)).block();
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while adding to database.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (success.get()){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Prefix successfully updated.")).block();
                                        updateProperties();
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Similarity set)" + Arrays.toString(args) + " Success: " + success;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Permissions)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Scan (ID: 17):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "scan"))
                .map(Message -> {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    Optional<Snowflake> userSnowflake = Message.getAuthor().map(User::getId);
                    long authorID = userSnowflake.orElseThrow().asLong();

                    if (isAllowed(17, userRolesList, member, guildOwnerID)) {
                        AtomicBoolean success = new AtomicBoolean(false);
                        String[] args = Message.getContent().split(" (?=(([^'\"]*['\"]){2})*[^'\"]*$)");

                        if (args.length <= 1){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                        }else{
                            if (args[1].equalsIgnoreCase("all")){
                                Message.getChannel().flatMap(channel -> channel.createMessage(":receipt: Scan started")).block();
                                List<Member> guildMembers = Objects.requireNonNull(Message.getGuild().block()).getMembers().collectList().block();

                                DateTimeFormatter formatter =
                                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                                .withLocale(Locale.US)
                                                .withZone(ZoneId.systemDefault());

                                List<List<Member>> memberChunks;
                                if (guildMembers != null) {
                                    memberChunks = chunk(guildMembers,guildMembers.size()/2);
                                    success.set(true);

                                    for (List<Member> memberChunk : memberChunks) {
                                        new Thread(() -> memberChunk.forEach(guildMember -> {
                                            String memberAvatar = guildMember.getAvatarUrl();
                                            String guildMemberID = guildMember.getId().asString();

                                            if (!(ignoreList.contains(guildMemberID))){
                                                try {
                                                    List<AvatarDiffReceipt> avatarDiff = new ArrayList<>(imageRecognition.pixelSimilarity(memberAvatar));
                                                    if(findMatch(avatarDiff,similarity)){
                                                        for (Mono<Channel> channel : channelsToNotify) {
                                                            channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                                    "Found Possible Match: <@"
                                                                            + guildMember.getId().asLong()
                                                                            + "> (ID: " + guildMember.getId().asLong() + ")"
                                                                            + " for: `" + currentAvatarType + "`"
                                                                            + " Similarity Index: `" + currentIndex + "`")).block();
                                                        }

                                                        for (Mono<User> userMono : usersToNotify) {
                                                            try{
                                                                Objects.requireNonNull(Objects.requireNonNull(userMono.block()).getPrivateChannel().block()).createMessage(
                                                                        "Found Possible Match: <@"
                                                                                + guildMember.getId().asLong()
                                                                                + "> (ID: " + guildMember.getId().asLong() + ")"
                                                                                + " for: `" + currentAvatarType + "`").block();
                                                            } catch (ClientException e){
                                                                System.out.println("Client error: " + e);
                                                            }
                                                        }

                                                        notifiedAvatars.add(memberAvatar);

                                                        System.out.println("Found Possible Match: "
                                                                + guildMember.getDisplayName()
                                                                + "#" + guildMember.getDiscriminator()
                                                                + " (" + guildMember.getId().asLong() + ")"
                                                                +  "[" + currentAvatarType + "]");
                                                    }
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            Instant instant = Instant.now();
                                            String time = formatter.format(instant);

                                            System.out.println("\nAvatar Checked: "
                                                    + guildMember.getDisplayName() + "#"
                                                    + guildMember.getDiscriminator()
                                                    + " (" + guildMember.getId().asLong() + ")"
                                                    + " " + time
                                                    + "\nAvatar URL: "
                                                    + memberAvatar);
                                        })).start();
                                    }
                                }

                            }else if(args[1].equalsIgnoreCase("legacy")){
                                Message.getChannel().flatMap(channel -> channel.createMessage(":receipt: Scan started")).block();
                                List<Member> guildMembers = Objects.requireNonNull(Message.getGuild().block()).getMembers().collectList().block();

                                DateTimeFormatter formatter =
                                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                                .withLocale(Locale.US)
                                                .withZone(ZoneId.systemDefault());

                                if (guildMembers != null) {
                                    new Thread(() -> guildMembers.forEach(guildMember -> {
                                        String memberAvatar = guildMember.getAvatarUrl();
                                        String guildMemberID = guildMember.getId().asString();
                                        success.set(true);

                                        if (!(ignoreList.contains(guildMemberID))){
                                            try {
                                                List<AvatarDiffReceipt> avatarDiff = new ArrayList<>(imageRecognition.pixelSimilarity(memberAvatar));
                                                if(findMatch(avatarDiff,similarity)){
                                                    for (Mono<Channel> channel : channelsToNotify) {
                                                        channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                                "Found Possible Match: <@"
                                                                        + guildMember.getId().asLong()
                                                                        + "> (ID: " + guildMember.getId().asLong() + ")"
                                                                        + " for: `" + currentAvatarType + "`"
                                                                        + " Similarity Index: `" + currentIndex + "`")).block();
                                                    }

                                                    for (Mono<User> userMono : usersToNotify) {
                                                        try{
                                                            Objects.requireNonNull(Objects.requireNonNull(userMono.block()).getPrivateChannel().block()).createMessage(
                                                                    "Found Possible Match: <@"
                                                                            + guildMember.getId().asLong()
                                                                            + "> (ID: " + guildMember.getId().asLong() + ")"
                                                                            + " for: `" + currentAvatarType + "`").block();
                                                        } catch (ClientException e){
                                                            System.out.println("Client error: " + e);
                                                        }
                                                    }

                                                    notifiedAvatars.add(memberAvatar);

                                                    System.out.println("Found Possible Match: "
                                                            + guildMember.getDisplayName()
                                                            + "#" + guildMember.getDiscriminator()
                                                            + " (" + guildMember.getId().asLong() + ")"
                                                            +  "[" + currentAvatarType + "]");
                                                }
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        Instant instant = Instant.now();
                                        String time = formatter.format(instant);
                                        System.out.println("\nAvatar Checked: "
                                                + guildMember.getDisplayName() + "#"
                                                + guildMember.getDiscriminator()
                                                + " (" + guildMember.getId().asLong() + ")"
                                                + " " + time
                                                + "\nAvatar URL: "
                                                + memberAvatar);
                                    })).start();
                                }
                            }else if (args[1].equalsIgnoreCase("custom")){
                                Message.getChannel().flatMap(channel -> channel.createMessage(":receipt: Scan started")).block();
                                List<Member> guildMembers = Objects.requireNonNull(Message.getGuild().block()).getMembers().collectList().block();
                                List<List<Member>> memberChunks;

                                int divider = divisor;
                                boolean isCustomImageSearch = false;

                                if (args.length > 2){
                                    if (onlyDigits(args[2]) && !(isURLValid(args[2]))){
                                        try{
                                            divider = Integer.parseInt(args[2]);
                                            success.set(true);
                                        } catch (Exception e){
                                            Message.getChannel().flatMap(channel -> channel.createMessage(":x: Error while casting int, default scan value maintained.")).block();
                                        }
                                    }else if (isURLValid(args[2])){
                                        isCustomImageSearch = true;
                                        success.set(true);
                                    }else{
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":x: Error input while creating custom scan.")).block();
                                    }
                                }

                                DateTimeFormatter formatter =
                                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                                .withLocale(Locale.US)
                                                .withZone(ZoneId.systemDefault());
                                if (success.get() & !isCustomImageSearch){
                                    if (guildMembers != null) {
                                        memberChunks = chunkList(guildMembers,guildMembers.size()/divider);
                                        for (List<Member> memberChunk : memberChunks) {
                                            new Thread(() -> memberChunk.forEach(guildMember -> {
                                                String memberAvatar = guildMember.getAvatarUrl();
                                                String guildMemberID = guildMember.getId().asString();

                                                if (!(ignoreList.contains(guildMemberID))){
                                                    try {
                                                        List<AvatarDiffReceipt> avatarDiff = new ArrayList<>(imageRecognition.pixelSimilarity(memberAvatar));
                                                        if(findMatch(avatarDiff,similarity)){
                                                            for (Mono<Channel> channel : channelsToNotify) {
                                                                channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                                        "Found Possible Match: <@"
                                                                                + guildMember.getId().asLong()
                                                                                + "> (ID: " + guildMember.getId().asLong() + ")"
                                                                                + " for: `" + currentAvatarType + "`"
                                                                                + " Similarity Index: `" + currentIndex + "`")).block();
                                                            }

                                                            for (Mono<User> userMono : usersToNotify) {
                                                                try{
                                                                    Objects.requireNonNull(Objects.requireNonNull(userMono.block()).getPrivateChannel().block()).createMessage(
                                                                            "Found Possible Match: <@"
                                                                                    + guildMember.getId().asLong()
                                                                                    + "> (ID: " + guildMember.getId().asLong() + ")"
                                                                                    + " for: `" + currentAvatarType + "`").block();
                                                                } catch (ClientException e){
                                                                    System.out.println("Client error: " + e);
                                                                }
                                                            }

                                                            notifiedAvatars.add(memberAvatar);

                                                            System.out.println("Found Possible Match: "
                                                                    + guildMember.getDisplayName()
                                                                    + "#" + guildMember.getDiscriminator()
                                                                    + " (" + guildMember.getId().asLong() + ")"
                                                                    +  "[" + currentAvatarType + "]");
                                                        }
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }

                                                Instant instant = Instant.now();
                                                String time = formatter.format(instant);

                                                System.out.println("\nAvatar Checked: "
                                                        + guildMember.getDisplayName() + "#"
                                                        + guildMember.getDiscriminator()
                                                        + " (" + guildMember.getId().asLong() + ")"
                                                        + " " + time
                                                        + "\nAvatar URL: "
                                                        + memberAvatar);
                                            })).start();
                                        }
                                    }
                                }else if (success.get()){
                                    if (guildMembers != null) {
                                        memberChunks = chunkList(guildMembers,guildMembers.size()/divider);
                                        for (List<Member> memberChunk : memberChunks) {
                                            new Thread(() -> memberChunk.forEach(guildMember -> {
                                                String memberAvatar = guildMember.getAvatarUrl();
                                                String guildMemberID = guildMember.getId().asString();

                                                if (!(ignoreList.contains(guildMemberID))){
                                                    try {
                                                        List<AvatarDiffReceipt> avatarDiff = new ArrayList<>(imageRecognition.pixelSimilarityCustom(memberAvatar,args[2]));
                                                        if(findMatch(avatarDiff,similarity)){
                                                            for (Mono<Channel> channel : channelsToNotify) {
                                                                channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                                        "Found Possible Match: <@"
                                                                                + guildMember.getId().asLong()
                                                                                + "> (ID: " + guildMember.getId().asLong() + ")"
                                                                                + " for: `" + currentAvatarType + "`"
                                                                                + " Similarity Index: `" + currentIndex + "`")).block();
                                                            }

                                                            notifiedAvatars.add(memberAvatar);

                                                            System.out.println("Found Possible Match: "
                                                                    + guildMember.getDisplayName()
                                                                    + "#" + guildMember.getDiscriminator()
                                                                    + " (" + guildMember.getId().asLong() + ")"
                                                                    +  "[" + currentAvatarType + "]");
                                                        }
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }

                                                Instant instant = Instant.now();
                                                String time = formatter.format(instant);

                                                System.out.println("\nAvatar Checked: "
                                                        + guildMember.getDisplayName() + "#"
                                                        + guildMember.getDiscriminator()
                                                        + " (" + guildMember.getId().asLong() + ")"
                                                        + " " + time
                                                        + "\nAvatar URL: "
                                                        + memberAvatar);
                                            })).start();
                                        }
                                    }
                                }
                            }else{
                                String userID = "";

                                if (args[1].contains("<@")){
                                    userID = args[1].replaceAll("[^\\d.]", "");
                                }
                                String userAvatarToScan;
                                if(!(userID.equals("")) && onlyDigits(userID)){
                                    Member memberToScan = Objects.requireNonNull(Message.getGuild().block()).getMemberById(Snowflake.of(userID)).block();
                                    if (memberToScan != null) {
                                        userAvatarToScan = memberToScan.getAvatarUrl();
                                        try {
                                            List<AvatarDiffReceipt> avatarDiff = new ArrayList<>(imageRecognition.pixelSimilarity(userAvatarToScan));
                                            if(findMatch(avatarDiff,similarity)){
                                                for (Mono<Channel> channel : channelsToNotify) {
                                                    channel.flatMap(nMessageChannel -> nMessageChannel.getRestChannel().createMessage(
                                                            "Found Possible Match: <@"
                                                                    + memberToScan.getId().asLong()
                                                                    + "> (ID: " + memberToScan.getId().asLong() + ")"
                                                                    + " for: `" + currentAvatarType + "`"
                                                                    + " Similarity Index: `" + currentIndex + "`")).block();
                                                }

                                                for (Mono<User> userMono : usersToNotify) {
                                                    try{
                                                        Objects.requireNonNull(Objects.requireNonNull(userMono.block()).getPrivateChannel().block()).createMessage(
                                                                "Found Possible Match: <@"
                                                                        + memberToScan.getId().asLong()
                                                                        + "> (ID: " + memberToScan.getId().asLong() + ")"
                                                                        + " for: `" + currentAvatarType + "`").block();
                                                    } catch (ClientException e){
                                                        System.out.println("Client error: " + e);
                                                    }

                                                }
                                                notifiedAvatars.add(userAvatarToScan);

                                                System.out.println("Found Possible Match: "
                                                        + memberToScan.getDisplayName()
                                                        + "#" + memberToScan.getDiscriminator()
                                                        + " (" + memberToScan.getId().asLong() + ")"
                                                        +  "[" + currentAvatarType + "]");
                                            }else {
                                                Message.getChannel().flatMap(channel -> channel.createMessage(":x: <@" + authorID + "> No matches found.")).block();
                                            }
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                        System.out.println("\nAvatar Checked: "
                                                + memberToScan.getDisplayName() + "#"
                                                + memberToScan.getDiscriminator()
                                                + " (" + memberToScan.getId().asLong() + ")"
                                                + "\nAvatar URL: "
                                                + userAvatarToScan);
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Scan)" + Arrays.toString(args) + " Success: " + success.get();
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid permissions")).block();
                        return "Command Denied: (Command info)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Similarity Set (ID: 18):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "similarity set")
                        || message.getContent().toLowerCase().contains(prefix + "s set")
                        || message.getContent().toLowerCase().contains(prefix + "sim set"))
                .map(Message ->  {

                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if(isAllowed(18,userRolesList,member,guildOwnerID)){
                        AtomicBoolean success = new AtomicBoolean(false);
                        String[] args = Message.getContent().split("\\s");

                        if (args.length <= 2){
                            Message.getChannel().flatMap(channel -> channel.createMessage("Invalid number of arguments.")).block();
                            success.set(false);
                        }else{
                            if (args[2] != null){
                                String simArg = args[2];
                                double similarityIndex;

                                if (!onlyDigits(simArg)){
                                    simArg = simArg.replaceAll("[^\\d.]", "");
                                }

                                similarityIndex = Double.parseDouble(simArg);

                                PreparedStatement preparedStatement = null;

                                try {
                                    String query = "UPDATE properties set similarity_index_prefix = ? where id = 1";

                                    preparedStatement = database.connect().prepareStatement(query);
                                    preparedStatement.setDouble(1, similarityIndex);

                                    preparedStatement.execute();
                                    success.set(true);

                                }catch (SQLIntegrityConstraintViolationException e){
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred. Error:" + e)).block();
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Message.getChannel().flatMap(channel -> channel.createMessage("Error has occurred while adding to database.")).block();
                                }finally {
                                    database.disconnect();
                                    if (preparedStatement != null){
                                        try {
                                            preparedStatement.close();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (success.get()){
                                        Message.getChannel().flatMap(channel -> channel.createMessage(":ballot_box_with_check: Similarity token successfully set.")).block();
                                        updateProperties();
                                    }
                                }
                            }
                        }

                        return "Command Ran: (Similarity set)" + Arrays.toString(args) + " Success: " + success;
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Permissions)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);

        //Similarity Search  (ID: 19):
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().toLowerCase().contains(prefix + "similarity search")
                        || message.getContent().toLowerCase().contains(prefix + "similarity")
                        || message.getContent().toLowerCase().contains(prefix + "sim search")
                        || message.getContent().toLowerCase().contains(prefix + "sim"))
                .map(Message -> {
                    Mono<Member> member = Message.getAuthorAsMember();
                    List<Role> userRolesList = new ArrayList<>(Objects.requireNonNull(Objects.requireNonNull(Message.getAuthorAsMember().block()).getRoles().collectList().block()));
                    Snowflake guildOwnerID = Objects.requireNonNull(Message.getGuild().block()).getOwnerId();

                    if (isAllowed(19,userRolesList,member,guildOwnerID)){
                        Message.getChannel().flatMap(channel -> channel.createMessage("Similarity: `" + similarity + "`")).block();
                        return "Command Ran: (Similarity search) Success: True";
                    }else{
                        Message.getChannel().flatMap(channel -> channel.createMessage("Invalid Permissions.")).block();
                        return "Command Denied: (Permissions)[Invalid Permissions]";
                    }
                })
                .onErrorResume((e) -> {
                    e.printStackTrace();
                    return Mono.empty();
                })
                .doOnError(Throwable::printStackTrace)
                .subscribe(System.out::println);


        client.onDisconnect().block();
    }
}
