package eu.eahub.entities.stats;

/**
 * The scorecard columns shared by a head-to-head match report and a tournament round
 */
public interface TwScorecard {

    int getRnk();

    void setRnk(int rnk);

    int getDone();

    void setDone(int done);

    int getQuit();

    void setQuit(int quit);

    int getType();

    void setType(int type);

    int getScore();

    void setScore(int score);

    int getClub();

    void setClub(int club);

    int getStrokes();

    void setStrokes(int strokes);

    int getPutts();

    void setPutts(int putts);

    int getHoles();

    void setHoles(int holes);

    int getEags();

    void setEags(int eags);

    int getBird();

    void setBird(int bird);

    int getAces();

    void setAces(int aces);

    int getGir();

    void setGir(int gir);

    int getLput();

    void setLput(int lput);

    int getDrvs();

    void setDrvs(int drvs);

    int getFrwy();

    void setFrwy(int frwy);

    int getLdrv();

    void setLdrv(int ldrv);

    int getShtc();

    void setShtc(int shtc);

    int getPars();

    void setPars(int pars);

    int getSbog();

    void setSbog(int sbog);

    int getDbog();

    void setDbog(int dbog);

    int getTbog();

    void setTbog(int tbog);

    int getComp();

    void setComp(int comp);
}
