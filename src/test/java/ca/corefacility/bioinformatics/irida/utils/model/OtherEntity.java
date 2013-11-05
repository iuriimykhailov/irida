
package ca.corefacility.bioinformatics.irida.utils.model;

import ca.corefacility.bioinformatics.irida.model.IridaThing;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import org.hibernate.envers.Audited;

/**
 *
 * @author Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>
 */
@Entity
@Table(name="otherEntity")
@Audited
public class OtherEntity implements IridaThing, Comparable<OtherEntity>{
	@Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String label;
        
    private Date createdDate;
    
    private Date modifiedDate;
	
	@OneToMany(fetch = FetchType.LAZY,cascade = CascadeType.REMOVE,mappedBy = "otherEntity")
	private List<EntityJoin> identified;

    public OtherEntity() {
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("created: ").append(createdDate);
        return builder.toString();
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public int compareTo(OtherEntity o) {
        return createdDate.compareTo(o.createdDate);
    }

    @Override
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label){
        this.label = label;
    }

    @Override
    public Date getTimestamp() {
        return createdDate;
    }

    @Override
    public void setTimestamp(Date date) {
        this.createdDate = date;
    }

    @Override
    public int hashCode() {
        return Objects.hash(createdDate);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof OtherEntity) {
            OtherEntity u = (OtherEntity) other;
            return Objects.equals(createdDate, u.createdDate);
        }

        return false;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
	}
}