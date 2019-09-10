package greencity.dto.specification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecificationValueDto {
    private Long id;
    private String desc;
    private Long value;
    private SpecificationDto specification;
}
