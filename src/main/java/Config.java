
public enum Config {

    AT("AT"),
    RT("RT"),
    CLIENTID("clientId"),
    EXP("EXP"),
    USERID("uid");

    Config(String value){
        val = value;
    }

    private String val;

    public String getVal(){
        return val;
    }
}
