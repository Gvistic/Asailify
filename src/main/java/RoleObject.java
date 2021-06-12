import discord4j.common.util.Snowflake;
import java.util.Set;

public class RoleObject {
    private Set<Snowflake> roleIDs;
    private int commandID;
    private boolean isHigherRoleAllowed;

    public RoleObject(Set<Snowflake> allowedRoleIDs, int commandID, boolean isHigherRoleAllowed) {
        this.roleIDs = allowedRoleIDs;
        this.commandID = commandID;
        this.isHigherRoleAllowed = isHigherRoleAllowed;
    }

    public Set<Snowflake> getRoleIDs() {
        return roleIDs;
    }

    public String getRoleIDString(){
        StringBuilder formattedRoleIDs = new StringBuilder();
        for (Snowflake roleID : roleIDs) {
            formattedRoleIDs.append(roleID.asLong()).append(" (<@&").append(roleID.asLong()).append(">), ");
        }

        if (isHigherRoleAllowed){
            formattedRoleIDs.append(" +");
        }else{
            formattedRoleIDs.append(" -");
        }

        return formattedRoleIDs.toString();
    }

    public void setRoleIDs(Set<Snowflake> roleIDs) {
        this.roleIDs = roleIDs;
    }

    public boolean isHigherRoleAllowed() {
        return isHigherRoleAllowed;
    }

    public void setHigherRoleAllowed(boolean higherRoleAllowed) {
        isHigherRoleAllowed = higherRoleAllowed;
    }

    public int getCommandID() {
        return commandID;
    }

    public void setCommandID(int commandID) {
        this.commandID = commandID;
    }

}
