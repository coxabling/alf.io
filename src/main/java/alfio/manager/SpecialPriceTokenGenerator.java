/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Class SpecialPriceTokenGenerator.
 * This class generates a bunch of tokens which will be used for
 * granting a special price to a specific user category.
 */
@Component
@Log4j2
public class SpecialPriceTokenGenerator {

    private static final char[] ADMITTED_CHARACTERS = new char[]{
            'A', 'B', 'C', 'D', 'E', 'F',
            'G', 'H', 'J', 'K', 'M', 'N',
            'P', 'Q', 'R', 'S', 'T', 'W',
            'X', 'Y', 'Z', '2', '3', '4',
            '5', '6', '7', '8', '9'
    };

    private final SpecialPriceRepository specialPriceRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;

    @Autowired
    public SpecialPriceTokenGenerator(ConfigurationManager configurationManager,
                                      SpecialPriceRepository specialPriceRepository,
                                      TicketCategoryRepository ticketCategoryRepository,
                                      EventRepository eventRepository) {
        this.specialPriceRepository = specialPriceRepository;
        this.configurationManager = configurationManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.eventRepository = eventRepository;
    }

    void generatePendingCodes() {
        StopWatch stopWatch = new StopWatch();
        log.debug("start pending codes generation");
        stopWatch.start();
        specialPriceRepository.findWaitingElements().stream()
                .forEach(this::generateCode);
        stopWatch.stop();
        log.debug("end. Took {} ms", stopWatch.getTime());
    }

    private void generateCode(SpecialPrice specialPrice) {

        TicketCategory ticketCategory = ticketCategoryRepository.getById(specialPrice.getTicketCategoryId()).orElseThrow(IllegalStateException::new);
        Event event = eventRepository.findById(ticketCategory.getEventId());
        int maxLength = configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ticketCategory.getId(), ConfigurationKeys.SPECIAL_PRICE_CODE_LENGTH), 6);

        while (true) {
            try {
                log.debug("generate code for special price with id {}", specialPrice.getId());
                specialPriceRepository.updateCode(nextValidCode(maxLength), specialPrice.getId());
                log.debug("done.");
                return;
            } catch (DataAccessException e) {
                log.warn("got a duplicate. Retrying...", e);
            }
        }
    }

    private String nextValidCode(int maxLength) {
        while (true) {
            String code = generateRandomCode(maxLength);
            if (specialPriceRepository.countByCode(code) == 0) {
                return code;
            }
        }
    }

    private String generateRandomCode(int maxLength) {
        return RandomStringUtils.random(maxLength, ADMITTED_CHARACTERS);
    }


}
