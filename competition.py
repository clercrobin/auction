import subprocess
import random

nb_agents = 2
cities = ["London", "Newcastle", "Leeds", "Sheffield", "Birmingham", "Cardiff", \
    "Plymouth", "Norwich", "Liverpool", "Manchester", "Portsmouth"]
# Important to set a timeout sufficiently large
timeout = 45

def parse_output(output):
    bids = [[] for _ in range(nb_agents)]
    winners = []
    costs = [[] for _ in range(nb_agents)]
    lines = output.split('\n')
    for line in lines:
        values = line.split(' ')
        if values[0] == 'ASK_PRICE':
            agent_id, plan_cost, marginal_cost, task_length, bid = int(values[1]), float(values[2]), \
                float(values[3]), float(values[4]), int(values[5])
            #print(agent_id, plan_cost, marginal_cost, task_length, bid)
            bids[agent_id].append((plan_cost, marginal_cost, task_length, bid))
        elif values[0] == 'AUCTION_RESULT':
            agent_id, winner, cost = int(values[1]), int(values[2]), float(values[3])
            if agent_id == winner:
                winners.append(winner)
            costs[agent_id].append(cost)
    #print_bids(bids)
    print()
    return (bids, winners, costs)

def print_bids(bids):
    bids1 = bids[0]
    bids2 = bids[1]
    for row1, row2 in zip(bids1, bids2):
        row1 = list(map(str, row1))
        row2 = list(map(str, row2))
        row = row1 + row2
        print(', '.join(row))

def analyse_results(rounds):
    nb_victories = [0] * nb_agents
    gain = [[0.0] * len(rounds) for _ in range(nb_agents)]
    for i, results in enumerate(rounds):
        for winner, row1, row2 in zip(results[1], results[0][0], results[0][1]):
            bid1, bid2 = row1[3], row2[3]
            if winner == 0:
                gain[0][i] += bid1
            else:
                gain[1][i] += bid2
        gain[0][i] -= results[2][0][-1]
        gain[1][i] -= results[2][1][-1]
        if gain[0][i] >= gain[1][i]:
            nb_victories[0] += 1
        else:
            nb_victories[1] += 1
    print(gain)
    print(nb_victories)
    print(sum(gain[0]), sum(gain[1]))

def set_seed(seed, path):
    # Read
    f = open(path, 'r')
    s = f.read()
    f.close()

    # Write
    start = s.find('"', s.find('rngSeed')) + 1
    end = s.find('"', start)
    s = list(s)
    s[start:end] = str(seed)
    s = ''.join(s)
    f = open(path, 'w')
    f.write(s)
    f.close()

def create_vehicle():
    capacity = random.randint(20, 100)
    cost = random.randint(3, 8)
    city = random.choice(cities)
    return (capacity, cost, city)

def create_team(nb_vehicles):
    return [create_vehicle() for _ in range(nb_vehicles)]

def set_vehicle(s, name, vehicle):
    # City
    start = s.find('home="', s.find(name)) + 6
    end = s.find('"', start)
    s = list(s)
    s[start:end] = vehicle[2]
    s = ''.join(s)

    # Capacity
    start = s.find('capacity="', s.find(name)) + 10
    end = s.find('"', start)
    s = list(s)
    s[start:end] = list(str(vehicle[0]))
    s = ''.join(s)

    # Cost
    start = s.find('cost-per-km="', s.find(name)) + 13
    end = s.find('"', start)
    s = list(s)
    s[start:end] = list(str(vehicle[1]))
    s = ''.join(s)

    return s

def set_teams(team1, team2, path):
    # Read
    f = open(path, 'r')
    s = f.read()
    f.close()

    # Write
    s = set_vehicle(s, 'Vehicle 1', team1[0])
    s = set_vehicle(s, 'Vehicle 2', team1[1])
    s = set_vehicle(s, 'Vehicle 3', team2[0])
    s = set_vehicle(s, 'Vehicle 4', team2[1])
    f = open(path, 'w')
    f.write(s)
    f.close()

def run():
    proc = subprocess.Popen(["java", "-jar", "../logist/logist.jar", "config/auction.xml", name1, name2], \
        stdout=subprocess.PIPE)
    try:
        output, error = proc.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        proc.kill()
        output, error = proc.communicate()
        output = output.decode('ascii')
        return output

if __name__ == '__main__':
    nb_strategy = 5
    nb_rounds = 5
    for i in [5]:
        for j in range(nb_strategy):
            name1 = "auction-" + str(i+1)
            name2 = "auction-" + str(j+1)
            rounds = []
            for _ in range(nb_rounds):
                # Set config
                set_seed(random.randrange(10**7), 'config/auction.xml')
                team1, team2 = create_team(2), create_team(2)
                set_teams(team1, team2, 'config/auction.xml')

                # Run 1
                output = run()
                rounds.append(parse_output(output))

                # Swap the comparny and run 2
                set_teams(team2, team1, 'config/auction.xml')
                output = run()
                rounds.append(parse_output(output))

            print(name1, name2)
            analyse_results(rounds)