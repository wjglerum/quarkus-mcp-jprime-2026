package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "speaker")
public class Speaker extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String bio;
}
