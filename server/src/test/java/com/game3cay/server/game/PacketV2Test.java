package com.game3cay.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.util.*;
import org.junit.jupiter.api.*;

class PacketV2Test {
  @Test
  void allGameAndPagePayloadsSurviveActualFilter() throws Exception {
    var engine = new DefaultGameEngine(new Random(1));
    engine.initRound(
        "r",
        1,
        List.of(
            new RoundParticipant(1, "a", "A", new BigDecimal("100.00")),
            new RoundParticipant(2, "b", "B", new BigDecimal("100.00"))),
        BigDecimal.ONE);
    var packets = new ArrayList<Packet>();
    packets.add(Packet.serverPush(PacketType.START_GAME, new GameStartInfo(1, "r", 1)));
    packets.add(
        Packet.serverPush(
            PacketType.DEAL_CARD, new DealCardPayload("r", engine.getPlayerCards("r", 1), 1)));
    packets.add(Packet.serverPush(PacketType.PLAYER_STATE, engine.getPlayerSnapshot("r", 1)));
    packets.add(Packet.serverPush(PacketType.GAME_UPDATED, engine.getPublicSnapshot("r")));
    packets.add(Packet.serverPush(PacketType.BET_UPDATED, engine.getBetSnapshot("r")));
    packets.add(
        Packet.request(
            PacketType.GAME_ACTION,
            new GameActionRequest("r", BetAction.RAISE, new BigDecimal("5"))));
    engine.handleAction("r", 2, BetAction.CALL, null);
    engine.handleAction("r", 1, BetAction.CALL, null);
    packets.add(Packet.serverPush(PacketType.ROUND_RESULT, engine.getRoundResult("r")));
    packets.add(Packet.serverPush(PacketType.HISTORY_RESULT, PageResult.empty(0, 20)));
    packets.add(
        Packet.serverPush(
            PacketType.LEADERBOARD_RESULT,
            new PageResult<>(
                List.of(new LeaderboardItem(1, 1, "A", BigDecimal.ONE, 1, 1, 0)), 0, 20, 1, 1)));
    try (var server = new ServerSocket(0);
        var outgoing = new Socket("127.0.0.1", server.getLocalPort());
        var incoming = server.accept()) {
      var out = PacketIO.openOutput(outgoing);
      out.flush();
      var input = PacketIO.openInput(incoming);
      for (var p : packets) {
        PacketIO.write(out, p);
        assertEquals(p.getType(), PacketIO.read(input).getType());
      }
    }
  }
}
