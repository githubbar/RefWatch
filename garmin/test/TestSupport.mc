import Toybox.Lang;

// Shared fixture for unit tests. Not annotated (:test), so the runner does not call it.
function testMatch() as MatchState {
    return new MatchState({
        "id" => "test-game",
        "homeName" => "Eagles",
        "awayName" => "Hawks",
        "homeColor" => 0xFF0000,
        "awayColor" => 0x0055FF,
        "halfMinutes" => 30,
        "halftimeMinutes" => 5,
        "kickOffTeam" => TEAM_HOME,
        "scheduledStartMs" => null
    });
}
