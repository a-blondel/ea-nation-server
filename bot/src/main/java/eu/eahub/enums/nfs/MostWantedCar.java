package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Car models available in Need For Speed: Most Wanted 5-1-0 (PSP).
 */
@Getter
public enum MostWantedCar {
    CARRERA_4S(0, "CARRERA 4S"),
    CARRERA_GT(1, "CARRERA GT"),
    COBALT_SS(2, "COBALT SS"),
    CORVETTE(3, "CORVETTE"),
    ECLIPSE(4, "ECLIPSE"),
    FORD_GT(5, "FORD GT"),
    GALLARDO(6, "GALLARDO"),
    GOLF(7, "GOLF"),
    LANCER(8, "LANCER"),
    M3_GTR_1(9, "M3 GTR 1"),
    M3_GTR_2(10, "M3 GTR 2"),
    MAZDA3(11, "MAZDA3"),
    MUSTANG_GT(12, "MUSTANG GT"),
    RX_8(13, "RX-8"),
    TT(14, "TT"),
    WRX_STI(15, "WRX STI");

    private final int id;
    private final String model;

    MostWantedCar(int id, String model) {
        this.id = id;
        this.model = model;
    }

    public static MostWantedCar fromId(int id) {
        for (MostWantedCar car : values()) {
            if (car.id == id) {
                return car;
            }
        }
        return null;
    }

    public static String getModelName(Integer carId) {
        if (carId == null) return "UNKNOWN";
        MostWantedCar car = fromId(carId);
        return car != null ? car.model : "UNKNOWN";
    }
}
