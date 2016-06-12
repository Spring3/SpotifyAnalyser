package util;

public enum Config {

    AT("AT"),
    RT("RT"),
    EXP("EXP"),
    USERID("uid"),
    URI("uri"),
    PORT("port"),
    COLLECTION("COLLECTION");

    Config(String value){
        val = value;
    }

    private String val;

    public String getVal(){
        return val;
    }
}
