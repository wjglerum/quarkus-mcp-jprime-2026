package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "speaker")
public class Speaker extends PanacheEntity {

    @Column(name = "external_id")
    public String externalId;

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String bio;

    public String company;

    @Column(name = "twitter_handle")
    public String twitterHandle;

    @ManyToMany(mappedBy = "speakers")
    public Set<Session> sessions = new HashSet<>();

    public static Speaker findByExternalId(String externalId) {
        return find("externalId", externalId).firstResult();
    }
}
