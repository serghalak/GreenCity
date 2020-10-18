package greencity.dto.habit;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@Builder
public class HabitVO {
    private Long id;
    private String image;
}