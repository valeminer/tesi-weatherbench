package it.tesis.weatherbench.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.tesis.weatherbench.model.entity.WeatherStationEntity;

@Repository
public interface WeatherStationRepository extends JpaRepository<WeatherStationEntity, Long> {

    Optional<WeatherStationEntity> findByStationCode(String stationCode);

    @Query("select s.id from WeatherStationEntity s where s.stationCode = :stationCode")
    Optional<Long> findIdByStationCode(String stationCode);

    @Modifying
    @Query("delete from WeatherStationEntity s")
    int deleteAllInBulk();
}
