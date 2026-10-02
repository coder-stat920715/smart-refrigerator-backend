package com.smartfridge.dto;

import java.util.List;
import java.util.UUID;

public record OtaDeployResponse(UUID firmwareId, String version, int accepted, int rejected,
                                List<OtaDeployItem> results) {
}
