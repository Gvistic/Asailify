import fr.minuskube.pastee.JPastee;
import fr.minuskube.pastee.data.Paste;
import fr.minuskube.pastee.data.Section;
import fr.minuskube.pastee.response.SubmitResponse;

import java.util.ArrayList;

public class Pastee{
    JPastee pasteClient;

    public void setApiKey(String apiKey){
        this.pasteClient = new JPastee(apiKey);
    }

    public String buildBlackList(ArrayList<String> list){
        StringBuilder pasteDescription = new StringBuilder();
        for (String s : list) {
            pasteDescription.append(s)
                    .append("\n");
        }
        Paste paste = Paste.builder()
                .description("Avatar/Profile Picture Blacklist List")
                .encrypted(true)
                .addSection(Section.builder()
                        .name("Avatar Blacklist:")
                        .contents(String.valueOf(pasteDescription))
                        .build())
                .build();

        SubmitResponse response = pasteClient.submit(paste);

        if (response.isSuccess()){
            System.out.println("POST: Paste submitted.");
            return response.getLink();
        }else{
            System.out.println("POST ERROR: Paste error: " + response.getErrorString());
            return "Error creating blacklist link.";
        }
    }

    public String buildNotifyList(ArrayList<String> list){
        StringBuilder pasteDescription = new StringBuilder();
        for (String s : list) {
            pasteDescription.append(s)
                    .append("\n");
        }
        Paste paste = Paste.builder()
                .description("List of subscribed users/channels to be notified.")
                .encrypted(true)
                .addSection(Section.builder()
                        .name("Notification List:")
                        .contents(String.valueOf(pasteDescription))
                        .build())
                .build();

        SubmitResponse response = pasteClient.submit(paste);

        if (response.isSuccess()){
            System.out.println("POST: Paste submitted.");
            return response.getLink();
        }else{
            System.out.println("POST ERROR: Paste error: " + response.getErrorString());
            return "Error creating notify link.";
        }
    }

    public String buildIgnoreList(ArrayList<String> list){
        StringBuilder pasteDescription = new StringBuilder();
        for (String s : list) {
            pasteDescription.append(s)
                    .append("\n");
        }
        Paste paste = Paste.builder()
                .description("Users to be ignored while scanning.")
                .encrypted(true)
                .addSection(Section.builder()
                        .name("Ignore List:")
                        .contents(String.valueOf(pasteDescription))
                        .build())
                .build();

        SubmitResponse response = pasteClient.submit(paste);

        if (response.isSuccess()){
            System.out.println("POST: Paste submitted.");
            return response.getLink();
        }else{
            System.out.println("POST ERROR: Paste error: " + response.getErrorString());
            return "Error creating ignore list link.";
        }
    }
}
