import sys
import logging
import asyncio

from asyncua import ua, Server
from random import gauss
from random import seed
from datetime import datetime

logging.basicConfig(level=logging.INFO)
_logger = logging.getLogger('asyncua')

seed(1)


def calc_temp(current_temp, count, modifier):
    temp = current_temp + gauss(0, 1)

    if count % modifier == 0:
        temp = current_temp + gauss(0, 1) + 20.0
    return temp

async def main():
    # setup our server
    server = Server()
    endpoint_url = "opc.tcp://0.0.0.0:4840/freeopcua/server/"
    if len(sys.argv) > 1:
        endpoint_url = sys.argv[1]
    print("Use Server Endpoint: " + endpoint_url)
    await server.init()
    server.set_endpoint(endpoint_url)
    # setup our own namespace, not really necessary but should as spec
    uri = 'http://examples.codecentric.de'
    idx = await server.register_namespace(uri)
    # get Objects node, this is where we should put our nodes
    objects = server.get_objects_node()
    # populating our address space
    _logger.info('Starting server!')

    belt1 = await objects.add_object(idx, "Convoyer Belt1")
    arm = await objects.add_object(idx, "Robot Arm")

    pre_stage_temp = 25.0
    mid_stage_temp = 65.0
    post_stage_temp = 90.0

    # motor has a possible degree from 0 to 180
    motor_degree = 0

    pre_stage = await belt1.add_variable(idx, "PreStage", pre_stage_temp)
    mid_stage = await belt1.add_variable(idx, "MidStage", mid_stage_temp)
    post_stage = await belt1.add_variable(idx, "PostStage", post_stage_temp)

    motor_degree_var = await arm.add_variable(idx, "Motor", motor_degree)

    ts = await belt1.add_variable(idx, "TimeStamp", datetime.now().isoformat())
    ts_arm = await arm.add_variable(idx, "TimeStamp", datetime.now().isoformat())
    async with server:
        count = 0
        current_pos = 0
        positive_direction = True
        while True:
            await asyncio.sleep(0.1)
            count += 1
            now = datetime.now().isoformat()
            temp = calc_temp(pre_stage_temp, count, 200)
            temp2 = calc_temp(mid_stage_temp, count, 1000)
            temp3 = calc_temp(post_stage_temp, count, 500)

            await pre_stage.set_value(temp)
            await mid_stage.set_value(temp2)
            await post_stage.set_value(temp3)
            await ts.set_value(now)

            step_size = 1

            # every 500 steps, delay:
            if count % 500 == 0:
                _logger.info("Delay Robot Arm")
                step_size = 0

            if positive_direction and current_pos < 180:
                current_pos += step_size
            elif positive_direction and current_pos >= 180:
                positive_direction = False
                current_pos -= step_size
            elif not positive_direction and 0 < current_pos < 180:
                current_pos -= step_size
            elif not positive_direction and current_pos <= 0:
                positive_direction = True
                current_pos += step_size

            await ts_arm.set_value(now)
            await motor_degree_var.set_value(current_pos)
            _logger.info('Set value of %s to %.1f', temp, count)
            _logger.info('Arm Position of %s to %.1f', str(positive_direction), current_pos)

            if count == 10000:
                count = 0


if __name__ == '__main__':
    loop = asyncio.get_event_loop()
    # loop.set_debug(True)
    loop.run_until_complete(main())
    loop.close()
